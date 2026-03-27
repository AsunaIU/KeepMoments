package http

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"keepmoments/backend/internal/logic"
	"keepmoments/backend/internal/repository"
)

const maxPhotoSize = 10 << 20

type Handler struct {
	templateService *logic.TemplateService
	photoService    *logic.PhotoService
	logger          *slog.Logger
}

func NewHandler(templateService *logic.TemplateService, photoService *logic.PhotoService, logger *slog.Logger) *Handler {
	return &Handler{
		templateService: templateService,
		photoService:    photoService,
		logger:          logger,
	}
}

func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

type createTemplateRequest struct {
	Name            string          `json:"name"`
	DescriptionJSON json.RawMessage `json:"description_json"`
}

func (h *Handler) CreateTemplate(w http.ResponseWriter, r *http.Request) {
	var req createTemplateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	if strings.TrimSpace(req.Name) == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}

	descriptionJSON, err := normalizeJSON(req.DescriptionJSON)
	if err != nil {
		writeError(w, http.StatusBadRequest, "description_json must be valid json")
		return
	}

	template, err := h.templateService.Create(r.Context(), logic.CreateTemplateInput{
		Name:            req.Name,
		DescriptionJSON: descriptionJSON,
	})
	if err != nil {
		h.logger.Error("create template failed", "error", err)
		writeError(w, http.StatusInternalServerError, "failed to create template")
		return
	}

	writeJSON(w, http.StatusCreated, template)
}

func (h *Handler) ListTemplates(w http.ResponseWriter, r *http.Request) {
	templates, err := h.templateService.List(r.Context())
	if err != nil {
		h.logger.Error("list templates failed", "error", err)
		writeError(w, http.StatusInternalServerError, "failed to list templates")
		return
	}

	writeJSON(w, http.StatusOK, templates)
}

func (h *Handler) GetTemplate(w http.ResponseWriter, r *http.Request) {
	id, err := parseInt64Path(r, "id")
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid template id")
		return
	}

	template, err := h.templateService.Get(r.Context(), id)
	if err != nil {
		if errors.Is(err, repository.ErrTemplateNotFound) {
			writeError(w, http.StatusNotFound, "template not found")
			return
		}

		h.logger.Error("get template failed", "error", err, "template_id", id)
		writeError(w, http.StatusInternalServerError, "failed to get template")
		return
	}

	writeJSON(w, http.StatusOK, template)
}

func (h *Handler) DeleteTemplate(w http.ResponseWriter, r *http.Request) {
	id, err := parseInt64Path(r, "id")
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid template id")
		return
	}

	if err := h.templateService.Delete(r.Context(), id); err != nil {
		switch {
		case errors.Is(err, repository.ErrTemplateNotFound):
			writeError(w, http.StatusNotFound, "template not found")
			return
		case errors.Is(err, repository.ErrTemplateHasPhotos):
			writeError(w, http.StatusConflict, "template has linked photos")
			return
		}

		h.logger.Error("delete template failed", "error", err, "template_id", id)
		writeError(w, http.StatusInternalServerError, "failed to delete template")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) CreatePhoto(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseMultipartForm(maxPhotoSize); err != nil {
		writeError(w, http.StatusBadRequest, "invalid multipart form")
		return
	}

	templateID, err := strconv.ParseInt(r.FormValue("template_id"), 10, 64)
	if err != nil || templateID <= 0 {
		writeError(w, http.StatusBadRequest, "template_id must be a positive integer")
		return
	}

	descriptionJSON, err := normalizeJSON([]byte(r.FormValue("description_json")))
	if err != nil {
		writeError(w, http.StatusBadRequest, "description_json must be valid json")
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		writeError(w, http.StatusBadRequest, "file is required")
		return
	}
	defer file.Close()

	payload, err := io.ReadAll(io.LimitReader(file, maxPhotoSize+1))
	if err != nil {
		writeError(w, http.StatusBadRequest, "failed to read file")
		return
	}

	if len(payload) == 0 {
		writeError(w, http.StatusBadRequest, "file is empty")
		return
	}

	if len(payload) > maxPhotoSize {
		writeError(w, http.StatusBadRequest, "file is too large")
		return
	}

	contentType := header.Header.Get("Content-Type")
	if strings.TrimSpace(contentType) == "" {
		contentType = http.DetectContentType(payload)
	}

	photo, err := h.photoService.Create(r.Context(), logic.CreatePhotoInput{
		TemplateID:      templateID,
		FileName:        header.Filename,
		ContentType:     contentType,
		FileBytes:       payload,
		DescriptionJSON: descriptionJSON,
	})
	if err != nil {
		switch {
		case errors.Is(err, repository.ErrTemplateReference):
			writeError(w, http.StatusBadRequest, "template_id does not exist")
		default:
			h.logger.Error("create photo failed", "error", err, "template_id", templateID)
			writeError(w, http.StatusInternalServerError, "failed to create photo")
		}
		return
	}

	writeJSON(w, http.StatusCreated, photo)
}

func (h *Handler) GetPhoto(w http.ResponseWriter, r *http.Request) {
	id, err := parseInt64Path(r, "id")
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid photo id")
		return
	}

	photo, err := h.photoService.Get(r.Context(), id)
	if err != nil {
		if errors.Is(err, repository.ErrPhotoNotFound) {
			writeError(w, http.StatusNotFound, "photo not found")
			return
		}

		h.logger.Error("get photo failed", "error", err, "photo_id", id)
		writeError(w, http.StatusInternalServerError, "failed to get photo")
		return
	}

	writeJSON(w, http.StatusOK, photo)
}

func (h *Handler) DeletePhoto(w http.ResponseWriter, r *http.Request) {
	id, err := parseInt64Path(r, "id")
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid photo id")
		return
	}

	if err := h.photoService.Delete(r.Context(), id); err != nil {
		if errors.Is(err, repository.ErrPhotoNotFound) {
			writeError(w, http.StatusNotFound, "photo not found")
			return
		}

		h.logger.Error("delete photo failed", "error", err, "photo_id", id)
		writeError(w, http.StatusInternalServerError, "failed to delete photo")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func parseInt64Path(r *http.Request, key string) (int64, error) {
	return strconv.ParseInt(r.PathValue(key), 10, 64)
}

func normalizeJSON(payload []byte) (json.RawMessage, error) {
	if len(bytes.TrimSpace(payload)) == 0 {
		return json.RawMessage(`{}`), nil
	}

	var value any
	if err := json.Unmarshal(payload, &value); err != nil {
		return nil, err
	}

	normalized, err := json.Marshal(value)
	if err != nil {
		return nil, err
	}

	return json.RawMessage(normalized), nil
}
