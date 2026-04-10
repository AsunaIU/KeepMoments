package app

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"keepmoments/backend/internal/config"
	"keepmoments/backend/internal/connections"
	"keepmoments/backend/internal/logic"
	httptransport "keepmoments/backend/internal/presentation/http"
	"keepmoments/backend/internal/repository"
)

type App struct {
	cfg        config.Config
	logger     *slog.Logger
	httpServer *http.Server
	db         *connections.Postgres
}

func New(ctx context.Context) (*App, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}

	logger := slog.Default()

	pg, err := connections.NewPostgres(ctx, cfg.Postgres)
	if err != nil {
		return nil, err
	}

	if err := connections.RunMigrations(cfg.Postgres.ConnectionString(), "db/migrations"); err != nil {
		_ = pg.Close(ctx)
		return nil, err
	}

	s3Client, err := connections.NewS3(ctx, cfg.S3)
	if err != nil {
		_ = pg.Close(ctx)
		return nil, err
	}

	if err := s3Client.EnsureBucket(ctx); err != nil {
		_ = pg.Close(ctx)
		return nil, err
	}

	templateRepo := repository.NewTemplateRepository(pg.Pool)
	photoRepo := repository.NewPhotoRepository(pg.Pool)
	userRepo := repository.NewUserRepository(pg.Pool)
	refreshTokenRepo := repository.NewRefreshTokenRepository(pg.Pool)
	templateService := logic.NewTemplateService(templateRepo)
	photoService := logic.NewPhotoService(photoRepo, s3Client.Storage)
	tokenService := logic.NewTokenService(cfg.Auth, refreshTokenRepo)
	authService := logic.NewAuthService(userRepo, tokenService)
	handler := httptransport.NewHandler(templateService, photoService, authService, tokenService, logger)

	server := &http.Server{
		Addr:              fmt.Sprintf(":%s", cfg.HTTPPort),
		Handler:           httptransport.NewRouter(handler),
		ReadHeaderTimeout: 5 * time.Second,
	}

	return &App{
		cfg:        cfg,
		logger:     logger,
		httpServer: server,
		db:         pg,
	}, nil
}

func (a *App) Run(ctx context.Context) error {
	errCh := make(chan error, 1)

	go func() {
		a.logger.Info("http server started", "addr", a.httpServer.Addr, "env", a.cfg.AppEnv)
		errCh <- a.httpServer.ListenAndServe()
	}()

	select {
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		if err := a.httpServer.Shutdown(shutdownCtx); err != nil {
			return err
		}

		if err := a.db.Close(shutdownCtx); err != nil {
			return err
		}

		return nil
	case err := <-errCh:
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = a.db.Close(shutdownCtx)

		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}

		return err
	}
}
