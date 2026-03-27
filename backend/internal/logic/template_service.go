package logic

import (
	"context"
	"encoding/json"
	"time"

	"keepmoments/backend/internal/model"
)

type TemplateRepository interface {
	Create(ctx context.Context, params model.CreateTemplateParams) (model.Template, error)
	GetByID(ctx context.Context, id int64) (model.Template, error)
	List(ctx context.Context) ([]model.Template, error)
	Delete(ctx context.Context, id int64) error
}

type TemplateService struct {
	repo TemplateRepository
}

func NewTemplateService(repo TemplateRepository) *TemplateService {
	return &TemplateService{repo: repo}
}

type CreateTemplateInput struct {
	Name            string
	DescriptionJSON json.RawMessage
}

type TemplateDetails struct {
	ID              int64           `json:"id"`
	Name            string          `json:"name"`
	DescriptionJSON json.RawMessage `json:"description_json"`
	CreatedAt       time.Time       `json:"created_at"`
}

func (s *TemplateService) Create(ctx context.Context, input CreateTemplateInput) (TemplateDetails, error) {
	template, err := s.repo.Create(ctx, model.CreateTemplateParams{
		Name:            input.Name,
		DescriptionJSON: []byte(input.DescriptionJSON),
	})
	if err != nil {
		return TemplateDetails{}, err
	}

	return mapTemplate(template), nil
}

func (s *TemplateService) Get(ctx context.Context, id int64) (TemplateDetails, error) {
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

func (s *TemplateService) Delete(ctx context.Context, id int64) error {
	return s.repo.Delete(ctx, id)
}

func mapTemplate(template model.Template) TemplateDetails {
	return TemplateDetails{
		ID:              template.ID,
		Name:            template.Name,
		DescriptionJSON: json.RawMessage(template.DescriptionJSON),
		CreatedAt:       template.CreatedAt,
	}
}
