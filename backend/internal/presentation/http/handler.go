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
	processService  *logic.ProcessService
	authService     *logic.AuthService
	tokenService    *logic.TokenService
	logger          *slog.Logger
}

func NewHandler(
	templateService *logic.TemplateService,
	photoService *logic.PhotoService,
	processService *logic.ProcessService,
	authService *logic.AuthService,
	tokenService *logic.TokenService,
	logger *slog.Logger,
) *Handler {
	return &Handler{
		templateService: templateService,
		photoService:    photoService,
		processService:  processService,
		authService:     authService,
		tokenService:    tokenService,
		logger:          logger,
	}
}

// Health godoc
// @Summary Health check
// @Tags system
// @Produce json
// @Success 200 {object} HealthResponse
// @Router /health [get]
func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// Process godoc
// @Summary Process
// @Tags ml
// @Accept json
// @Produce json
// @Param request body logic.ProcessRequest true "Process request"
// @Success 200 {object} logic.ProcessResponse
// @Failure 400 {object} ErrorResponse
// @Failure 422 {object} logic.HTTPValidationError
// @Router /process [post]
func (h *Handler) Process(w http.ResponseWriter, r *http.Request) {
	var req logic.ProcessRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	result, validation := h.processService.Process(r.Context(), req)
	if validation != nil {
		writeJSON(w, http.StatusUnprocessableEntity, validation)
		return
	}

	writeJSON(w, http.StatusOK, result)
}

type authRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

// Register godoc
// @Summary Register user
// @Tags auth
// @Accept json
// @Produce json
// @Param request body AuthRequest true "Credentials"
// @Success 201 {object} logic.AuthResponse
// @Failure 400 {object} ErrorResponse
// @Failure 409 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/auth/register [post]
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	var req authRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	result, err := h.authService.Register(r.Context(), logic.RegisterInput{
		Email:    req.Email,
		Password: req.Password,
	})
	if err != nil {
		switch {
		case errors.Is(err, logic.ErrInvalidCredentialsInput):
			writeError(w, http.StatusBadRequest, err.Error())
		case errors.Is(err, repository.ErrUserAlreadyExists):
			writeError(w, http.StatusConflict, "user already exists")
		default:
			h.logger.Error("register failed", "error", err)
			writeError(w, http.StatusInternalServerError, "failed to register")
		}
		return
	}

	writeJSON(w, http.StatusCreated, result)
}

// Login godoc
// @Summary Login user
// @Tags auth
// @Accept json
// @Produce json
// @Param request body AuthRequest true "Credentials"
// @Success 200 {object} logic.AuthResponse
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/auth/login [post]
func (h *Handler) Login(w http.ResponseWriter, r *http.Request) {
	var req authRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	result, err := h.authService.Login(r.Context(), logic.LoginInput{
		Email:    req.Email,
		Password: req.Password,
	})
	if err != nil {
		switch {
		case errors.Is(err, logic.ErrInvalidCredentialsInput):
			writeError(w, http.StatusBadRequest, err.Error())
		case errors.Is(err, repository.ErrInvalidCredentials):
			writeError(w, http.StatusUnauthorized, "invalid email or password")
		default:
			h.logger.Error("login failed", "error", err)
			writeError(w, http.StatusInternalServerError, "failed to login")
		}
		return
	}

	writeJSON(w, http.StatusOK, result)
}

// Refresh godoc
// @Summary Refresh tokens
// @Tags auth
// @Accept json
// @Produce json
// @Param request body RefreshRequest true "Refresh token"
// @Success 200 {object} logic.AuthResponse
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/auth/refresh [post]
func (h *Handler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	result, err := h.authService.Refresh(r.Context(), req.RefreshToken)
	if err != nil {
		switch {
		case errors.Is(err, logic.ErrInvalidRefreshToken):
			writeError(w, http.StatusUnauthorized, "invalid refresh token")
		default:
			h.logger.Error("refresh failed", "error", err)
			writeError(w, http.StatusInternalServerError, "failed to refresh token")
		}
		return
	}

	writeJSON(w, http.StatusOK, result)
}

type createTemplateRequest struct {
	Name            string          `json:"name"`
	DescriptionJSON json.RawMessage `json:"description_json"`
}

// CreateTemplate godoc
// @Summary Create template
// @Tags templates
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param request body CreateTemplateRequest true "Template payload"
// @Success 201 {object} logic.TemplateDetails
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/templates [post]
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

// ListTemplates godoc
// @Summary List templates
// @Tags templates
// @Produce json
// @Security BearerAuth
// @Success 200 {array} logic.TemplateDetails
// @Failure 401 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/templates [get]
func (h *Handler) ListTemplates(w http.ResponseWriter, r *http.Request) {
	templates, err := h.templateService.List(r.Context())
	if err != nil {
		h.logger.Error("list templates failed", "error", err)
		writeError(w, http.StatusInternalServerError, "failed to list templates")
		return
	}

	writeJSON(w, http.StatusOK, templates)
}

// GetTemplate godoc
// @Summary Get template
// @Tags templates
// @Produce json
// @Security BearerAuth
// @Param id path int true "Template ID"
// @Success 200 {object} logic.TemplateDetails
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/templates/{id} [get]
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

// DeleteTemplate godoc
// @Summary Delete template
// @Tags templates
// @Produce json
// @Security BearerAuth
// @Param id path int true "Template ID"
// @Success 204 {string} string "No Content"
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 409 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/templates/{id} [delete]
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

// CreatePhoto godoc
// @Summary Upload photo
// @Tags photos
// @Accept multipart/form-data
// @Produce json
// @Security BearerAuth
// @Param template_id formData int true "Template ID"
// @Param description_json formData string false "Photo description JSON"
// @Param file formData file true "Photo file"
// @Success 201 {object} logic.PhotoDetails
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/photos [post]
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

// GetPhoto godoc
// @Summary Get photo metadata
// @Tags photos
// @Produce json
// @Security BearerAuth
// @Param id path int true "Photo ID"
// @Success 200 {object} logic.PhotoDetails
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/photos/{id} [get]
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

// DeletePhoto godoc
// @Summary Delete photo
// @Tags photos
// @Produce json
// @Security BearerAuth
// @Param id path int true "Photo ID"
// @Success 204 {string} string "No Content"
// @Failure 400 {object} ErrorResponse
// @Failure 401 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/photos/{id} [delete]
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
