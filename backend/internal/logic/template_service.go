package logic

import (
	"context"
	"encoding/json"
	"time"

	"keepmoments/backend/internal/model"
)

type TemplateRepository interface {
	Create(ctx context.Context, params model.CreateTemplateParams) (model.Template, error)
	GetByID(ctx context.Context, id string) (model.Template, error)
	List(ctx context.Context) ([]model.Template, error)
	Delete(ctx context.Context, id string) error
}

type TemplateService struct {
	repo TemplateRepository
}

func NewTemplateService(repo TemplateRepository) *TemplateService {
	return &TemplateService{repo: repo}
}

type CreateTemplateInput struct {
	Template ProcessTemplate
}

type TemplateDetails struct {
	ID        string        `json:"id"`
	Pages     []ProcessPage `json:"pages"`
	CreatedAt time.Time     `json:"created_at"`
}

func (s *TemplateService) Create(ctx context.Context, input CreateTemplateInput) (TemplateDetails, error) {
	payload, err := json.Marshal(input.Template)
	if err != nil {
		return TemplateDetails{}, err
	}

	template, err := s.repo.Create(ctx, model.CreateTemplateParams{
		ID:           input.Template.ID,
		TemplateJSON: payload,
	})
	if err != nil {
		return TemplateDetails{}, err
	}

	return mapTemplate(template), nil
}

func (s *TemplateService) Get(ctx context.Context, id string) (TemplateDetails, error) {
	template, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return TemplateDetails{}, err
	}

	return mapTemplate(template), nil
}

func (s *TemplateService) List(ctx context.Context) ([]TemplateDetails, error) {
	templates, err := s.repo.List(ctx)
	if err != nil {
		return nil, err
	}

	items := make([]TemplateDetails, 0, len(templates))
	for _, template := range templates {
		items = append(items, mapTemplate(template))
	}

	return items, nil
}

func (s *TemplateService) Delete(ctx context.Context, id string) error {
	return s.repo.Delete(ctx, id)
}

func mapTemplate(template model.Template) TemplateDetails {
	var processTemplate ProcessTemplate
	_ = json.Unmarshal(template.TemplateJSON, &processTemplate)

	return TemplateDetails{
		ID:        processTemplate.ID,
		Pages:     processTemplate.Pages,
		CreatedAt: template.CreatedAt,
	}
}
