package http

import "net/http"

func NewRouter(handler *Handler) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", handler.Health)
	mux.HandleFunc("POST /api/v1/templates", handler.CreateTemplate)
	mux.HandleFunc("GET /api/v1/templates", handler.ListTemplates)
	mux.HandleFunc("GET /api/v1/templates/{id}", handler.GetTemplate)
	mux.HandleFunc("DELETE /api/v1/templates/{id}", handler.DeleteTemplate)
	mux.HandleFunc("POST /api/v1/photos", handler.CreatePhoto)
	mux.HandleFunc("GET /api/v1/photos/{id}", handler.GetPhoto)
	mux.HandleFunc("DELETE /api/v1/photos/{id}", handler.DeletePhoto)

	return mux
}
