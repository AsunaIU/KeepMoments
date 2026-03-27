package main

import (
	"context"
	"log"
	"net/http"
	"os/signal"
	"syscall"

	"keepmoments/backend/internal/app"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	application, err := app.New(ctx)
	if err != nil {
		log.Fatalf("build app: %v", err)
	}

	if err := application.Run(ctx); err != nil && err != http.ErrServerClosed {
		log.Fatalf("run app: %v", err)
	}
}
