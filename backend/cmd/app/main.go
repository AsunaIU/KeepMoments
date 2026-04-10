package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os/signal"
	"syscall"

	_ "keepmoments/backend/docs"
	"keepmoments/backend/internal/app"
)

// @title KeepMoments Backend API
// @version 1.0
// @description API for authentication, templates, and photos storage.
// @BasePath /
// @schemes http
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Enter the access token with the `Bearer ` prefix, for example `Bearer eyJhbGciOi...`
func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	application, err := app.New(ctx)
	if err != nil {
		log.Fatalf("build app: %v", err)
	}

	if err := application.Run(ctx); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("run app: %v", err)
	}
}
