package logic

import (
	"context"
	"strings"
)

type ProcessService struct{}

func NewProcessService() *ProcessService {
	return &ProcessService{}
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

type ProcessTemplate struct {
	ID    string        `json:"id"`
	Pages []ProcessPage `json:"pages"`
}

type ProcessPage struct {
	ID    string        `json:"id"`
	Slots []ProcessSlot `json:"slots"`
}

type ProcessSlot struct {
	ID      string  `json:"id"`
	PhotoID *string `json:"photo_id,omitempty"`
}

type ProcessResponse struct {
	FilledTemplate FilledTemplate `json:"filled_template"`
}

type FilledTemplate struct {
	ID    string       `json:"id"`
	Pages []FilledPage `json:"pages"`
}

type FilledPage struct {
	ID    string       `json:"id"`
	Slots []FilledSlot `json:"slots"`
}

type FilledSlot struct {
	ID      string  `json:"id"`
	PhotoID *string `json:"photo_id,omitempty"`
}

type ValidationError struct {
	Loc  []any  `json:"loc"`
	Msg  string `json:"msg"`
	Type string `json:"type"`
}

type HTTPValidationError struct {
	Detail []ValidationError `json:"detail"`
}

func (s *ProcessService) Process(_ context.Context, req ResolvedProcessRequest) (ProcessResponse, *HTTPValidationError) {
	if validation := validateResolvedProcessRequest(req); validation != nil {
		return ProcessResponse{}, validation
	}

	usablePhotoIDs := req.PhotoIDs
	if len(usablePhotoIDs) > req.MaxPhotos {
		usablePhotoIDs = usablePhotoIDs[:req.MaxPhotos]
	}

	photoIndex := 0
	filledPages := make([]FilledPage, 0, len(req.Template.Pages))
	for _, page := range req.Template.Pages {
		filledSlots := make([]FilledSlot, 0, len(page.Slots))
		for _, slot := range page.Slots {
			var assignedPhotoID *string
			if photoIndex < len(usablePhotoIDs) {
				photoID := usablePhotoIDs[photoIndex]
				assignedPhotoID = &photoID
				photoIndex++
			}

			filledSlots = append(filledSlots, FilledSlot{
				ID:      slot.ID,
				PhotoID: assignedPhotoID,
			})
		}

		filledPages = append(filledPages, FilledPage{
			ID:    page.ID,
			Slots: filledSlots,
		})
	}

	return ProcessResponse{
		FilledTemplate: FilledTemplate{
			ID:    req.Template.ID,
			Pages: filledPages,
		},
	}, nil
}

func ValidateProcessRequest(req ProcessRequest) *HTTPValidationError {
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
		}
	}

	return details
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
