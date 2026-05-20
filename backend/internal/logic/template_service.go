package logic

import (
	"context"

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

func (s *TemplateService) Create(ctx context.Context, input CreateTemplateInput) (ProcessTemplate, error) {
	template, err := s.repo.Create(ctx, model.CreateTemplateParams{
		ID:         input.Template.ID,
		Pages:      mapPagesToModel(input.Template.Pages),
		FrontCover: mapCoverToModel(input.Template.FrontCover),
		BackCover:  mapCoverToModel(input.Template.BackCover),
	})
	if err != nil {
		return ProcessTemplate{}, err
	}

	return mapTemplate(template), nil
}

func (s *TemplateService) Get(ctx context.Context, id string) (ProcessTemplate, error) {
	template, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return ProcessTemplate{}, err
	}

	return mapTemplate(template), nil
}

func (s *TemplateService) List(ctx context.Context) ([]ProcessTemplate, error) {
	templates, err := s.repo.List(ctx)
	if err != nil {
		return nil, err
	}

	items := make([]ProcessTemplate, 0, len(templates))
	for _, template := range templates {
		items = append(items, mapTemplate(template))
	}

	return items, nil
}

func (s *TemplateService) Delete(ctx context.Context, id string) error {
	return s.repo.Delete(ctx, id)
}

func mapTemplate(template model.Template) ProcessTemplate {
	return ProcessTemplate{
		ID:         template.ID,
		Pages:      mapPagesFromModel(template.Pages),
		FrontCover: mapCoverFromModel(template.FrontCover),
		BackCover:  mapCoverFromModel(template.BackCover),
	}
}

func mapPagesToModel(pages []ProcessPage) []model.TemplatePage {
	items := make([]model.TemplatePage, 0, len(pages))
	for _, page := range pages {
		modelPage := model.TemplatePage{
			ID:    page.ID,
			Slots: make([]model.TemplateSlot, 0, len(page.Slots)),
		}
		for _, slot := range page.Slots {
			modelPage.Slots = append(modelPage.Slots, model.TemplateSlot{
				ID:                  slot.ID,
				PhotoID:             slot.PhotoID,
				RequiredOrientation: slot.RequiredOrientation,
			})
		}
		items = append(items, modelPage)
	}
	return items
}

func mapPagesFromModel(pages []model.TemplatePage) []ProcessPage {
	items := make([]ProcessPage, 0, len(pages))
	for _, page := range pages {
		processPage := ProcessPage{
			ID:    page.ID,
			Slots: make([]ProcessSlot, 0, len(page.Slots)),
		}
		for _, slot := range page.Slots {
			processPage.Slots = append(processPage.Slots, ProcessSlot{
				ID:                  slot.ID,
				PhotoID:             slot.PhotoID,
				RequiredOrientation: slot.RequiredOrientation,
			})
		}
		items = append(items, processPage)
	}
	return items
}

func mapCoverToModel(cover *CoverConfig) *model.TemplateCover {
	if cover == nil {
		return nil
	}

	return &model.TemplateCover{
		Mode:    cover.Mode,
		PhotoID: cover.PhotoID,
		Text:    cover.Text,
	}
}

func mapCoverFromModel(cover *model.TemplateCover) *CoverConfig {
	if cover == nil {
		return nil
	}

	return &CoverConfig{
		Mode:    cover.Mode,
		PhotoID: cover.PhotoID,
		Text:    cover.Text,
	}
}
