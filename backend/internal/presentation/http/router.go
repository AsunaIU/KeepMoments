package http

import (
	"net/http"

	httpSwagger "github.com/swaggo/http-swagger/v2"
)

func NewRouter(handler *Handler) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", handler.Health)
	mux.HandleFunc("GET /swagger", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/swagger/index.html", http.StatusPermanentRedirect)
	})
	mux.Handle("/swagger/", httpSwagger.Handler(
		httpSwagger.URL("/swagger/doc.json"),
	))
	mux.HandleFunc("POST /api/v1/auth/register", handler.Register)
	mux.HandleFunc("POST /api/v1/auth/login", handler.Login)
	mux.HandleFunc("POST /api/v1/auth/refresh", handler.Refresh)

	mux.Handle("POST /api/v1/templates", handler.AuthMiddleware(http.HandlerFunc(handler.CreateTemplate)))
	mux.Handle("GET /api/v1/templates", handler.AuthMiddleware(http.HandlerFunc(handler.ListTemplates)))
	mux.Handle("GET /api/v1/templates/{id}", handler.AuthMiddleware(http.HandlerFunc(handler.GetTemplate)))
	mux.Handle("DELETE /api/v1/templates/{id}", handler.AuthMiddleware(http.HandlerFunc(handler.DeleteTemplate)))
	mux.Handle("POST /api/v1/photos", handler.AuthMiddleware(http.HandlerFunc(handler.CreatePhoto)))
	mux.Handle("GET /api/v1/photos/{id}", handler.AuthMiddleware(http.HandlerFunc(handler.GetPhoto)))
	mux.Handle("DELETE /api/v1/photos/{id}", handler.AuthMiddleware(http.HandlerFunc(handler.DeletePhoto)))

	return mux
}
