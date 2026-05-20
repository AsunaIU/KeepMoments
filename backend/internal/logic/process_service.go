package logic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"keepmoments/backend/internal/config"
)

type ProcessService struct {
	endpoint string
	client   *http.Client
}

func NewProcessService(cfg config.ProcessConfig) *ProcessService {
	return &ProcessService{
		endpoint: strings.TrimRight(cfg.Endpoint, "/"),
		client: &http.Client{
			Timeout: 60 * time.Second,
		},
	}
}

type ProcessRequest struct {
	PhotoIDs        []string `json:"photo_ids"`
	UserDescription string   `json:"user_description"`
	MinPhotos       int      `json:"min_photos"`
	MaxPhotos       int      `json:"max_photos"`
	TemplateID      string   `json:"template_id"`
}

type ResolvedProcessRequest struct {
	PhotoIDs        []string
	UserDescription string
	MinPhotos       int
	MaxPhotos       int
	Template        ProcessTemplate
}

type mlProcessRequest struct {
	PhotoIDs        []string        `json:"photo_ids"`
	UserDescription string          `json:"user_description"`
	MinPhotos       int             `json:"min_photos"`
	MaxPhotos       int             `json:"max_photos"`
	Template        ProcessTemplate `json:"template"`
}

type ProcessTemplate struct {
	ID         string        `json:"id"`
	Pages      []ProcessPage `json:"pages"`
	FrontCover *CoverConfig  `json:"front_cover,omitempty"`
	BackCover  *CoverConfig  `json:"back_cover,omitempty"`
}

type ProcessPage struct {
	ID    string        `json:"id"`
	Slots []ProcessSlot `json:"slots"`
}

type ProcessSlot struct {
	ID                  string  `json:"id"`
	PhotoID             *string `json:"photo_id,omitempty"`
	RequiredOrientation *string `json:"required_orientation,omitempty" enums:"portrait,landscape"`
}

type CoverConfig struct {
	Mode    string  `json:"mode" enums:"caption,photo"`
	PhotoID *string `json:"photo_id,omitempty"`
	Text    *string `json:"text,omitempty"`
}

type ProcessResponse struct {
	FilledTemplate FilledTemplate `json:"filled_template"`
}

type FilledTemplate struct {
	ID         string       `json:"id"`
	Pages      []FilledPage `json:"pages"`
	FrontCover *FilledCover `json:"front_cover,omitempty"`
	BackCover  *FilledCover `json:"back_cover,omitempty"`
}

type FilledPage struct {
	ID      string       `json:"id"`
	Slots   []FilledSlot `json:"slots"`
	Caption *string      `json:"caption,omitempty"`
}

type FilledSlot struct {
	ID      string  `json:"id"`
	PhotoID *string `json:"photo_id,omitempty"`
}

type FilledCover struct {
	Mode    string  `json:"mode" enums:"caption,photo"`
	PhotoID *string `json:"photo_id,omitempty"`
	Text    *string `json:"text,omitempty"`
}

type ValidationError struct {
	Loc  []any  `json:"loc"`
	Msg  string `json:"msg"`
	Type string `json:"type"`
}

type HTTPValidationError struct {
	Detail []ValidationError `json:"detail"`
}

type UpstreamProcessError struct {
	StatusCode  int
	ContentType string
	Body        []byte
}

func (e *UpstreamProcessError) Error() string {
	return fmt.Sprintf("process upstream returned status %d", e.StatusCode)
}

func (s *ProcessService) Process(ctx context.Context, req ResolvedProcessRequest) (ProcessResponse, *HTTPValidationError, error) {
	payload, err := json.Marshal(mlProcessRequest{
		PhotoIDs:        req.PhotoIDs,
		UserDescription: req.UserDescription,
		MinPhotos:       req.MinPhotos,
		MaxPhotos:       req.MaxPhotos,
		Template:        req.Template,
	})
	if err != nil {
		return ProcessResponse{}, nil, fmt.Errorf("marshal process request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpoint+"/process", bytes.NewReader(payload))
	if err != nil {
		return ProcessResponse{}, nil, fmt.Errorf("build process request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	resp, err := s.client.Do(httpReq)
	if err != nil {
		return ProcessResponse{}, nil, fmt.Errorf("call process upstream: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return ProcessResponse{}, nil, fmt.Errorf("read process upstream response: %w", err)
	}

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return ProcessResponse{}, nil, &UpstreamProcessError{
			StatusCode:  resp.StatusCode,
			ContentType: resp.Header.Get("Content-Type"),
			Body:        body,
		}
	}

	var result ProcessResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return ProcessResponse{}, nil, fmt.Errorf("decode process upstream response: %w", err)
	}

	return result, nil, nil
}

func ValidateProcessRequest(req ProcessRequest) *HTTPValidationError {
	var details []ValidationError

	if strings.TrimSpace(req.TemplateID) == "" {
		details = append(details, validationError([]any{"body", "template_id"}, "template_id is required", "value_error"))
	}

	if len(details) == 0 {
		return nil
	}

	return &HTTPValidationError{Detail: details}
}

func ValidateTemplate(template ProcessTemplate) *HTTPValidationError {
	details := validateTemplate(template, []any{"body"})
	if len(details) == 0 {
		return nil
	}

	return &HTTPValidationError{Detail: details}
}

func NormalizeTemplate(template *ProcessTemplate) {
	if template.FrontCover != nil && template.FrontCover.Mode == "" {
		template.FrontCover.Mode = "caption"
	}
	if template.BackCover != nil && template.BackCover.Mode == "" {
		template.BackCover.Mode = "caption"
	}
}

func validateResolvedProcessRequest(req ResolvedProcessRequest) *HTTPValidationError {
	var details []ValidationError

	if len(req.PhotoIDs) == 0 {
		details = append(details, validationError([]any{"body", "photo_ids"}, "photo_ids must not be empty", "value_error"))
	}

	if strings.TrimSpace(req.UserDescription) == "" {
		details = append(details, validationError([]any{"body", "user_description"}, "user_description is required", "value_error"))
	}

	if req.MinPhotos < 1 {
		details = append(details, validationError([]any{"body", "min_photos"}, "min_photos must be greater than or equal to 1", "value_error"))
	}

	if req.MaxPhotos < 1 {
		details = append(details, validationError([]any{"body", "max_photos"}, "max_photos must be greater than or equal to 1", "value_error"))
	}

	if req.MinPhotos > 0 && req.MaxPhotos > 0 && req.MaxPhotos < req.MinPhotos {
		details = append(details, validationError([]any{"body", "max_photos"}, "max_photos must be greater than or equal to min_photos", "value_error"))
	}

	if req.MinPhotos > 0 && len(req.PhotoIDs) < req.MinPhotos {
		details = append(details, validationError([]any{"body", "photo_ids"}, "photo_ids count is less than min_photos", "value_error"))
	}

	details = append(details, validateTemplate(req.Template, []any{"body", "template"})...)

	if len(details) == 0 {
		return nil
	}

	return &HTTPValidationError{Detail: details}
}

func validateTemplate(template ProcessTemplate, pathPrefix []any) []ValidationError {
	var details []ValidationError

	if template.ID == "" {
		details = append(details, validationError(appendPath(pathPrefix, "id"), "template.id is required", "value_error"))
	}

	if len(template.Pages) == 0 {
		details = append(details, validationError(appendPath(pathPrefix, "pages"), "template.pages must not be empty", "value_error"))
	}

	for pageIndex, page := range template.Pages {
		if page.ID == "" {
			details = append(details, validationError(appendPath(pathPrefix, "pages", pageIndex, "id"), "page id is required", "value_error"))
		}

		if len(page.Slots) == 0 {
			details = append(details, validationError(appendPath(pathPrefix, "pages", pageIndex, "slots"), "page slots must not be empty", "value_error"))
		}

		for slotIndex, slot := range page.Slots {
			if slot.ID == "" {
				details = append(details, validationError(appendPath(pathPrefix, "pages", pageIndex, "slots", slotIndex, "id"), "slot id is required", "value_error"))
			}

			if slot.RequiredOrientation != nil && !isAllowedOrientation(*slot.RequiredOrientation) {
				details = append(details, validationError(
					appendPath(pathPrefix, "pages", pageIndex, "slots", slotIndex, "required_orientation"),
					"required_orientation must be portrait or landscape",
					"value_error",
				))
			}
		}
	}

	if template.FrontCover != nil {
		details = append(details, validateCover(*template.FrontCover, appendPath(pathPrefix, "front_cover"))...)
	}

	if template.BackCover != nil {
		details = append(details, validateCover(*template.BackCover, appendPath(pathPrefix, "back_cover"))...)
	}

	return details
}

func validateCover(cover CoverConfig, pathPrefix []any) []ValidationError {
	if cover.Mode == "" {
		return []ValidationError{
			validationError(appendPath(pathPrefix, "mode"), "cover mode is required", "value_error"),
		}
	}

	if cover.Mode != "caption" && cover.Mode != "photo" {
		return []ValidationError{
			validationError(appendPath(pathPrefix, "mode"), "cover mode must be caption or photo", "value_error"),
		}
	}

	return nil
}

func isAllowedOrientation(value string) bool {
	return value == "portrait" || value == "landscape"
}

func appendPath(prefix []any, values ...any) []any {
	path := make([]any, 0, len(prefix)+len(values))
	path = append(path, prefix...)
	path = append(path, values...)
	return path
}

func validationError(loc []any, msg, errType string) ValidationError {
	return ValidationError{
		Loc:  loc,
		Msg:  msg,
		Type: errType,
	}
}
