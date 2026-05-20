package model

import "time"

type Template struct {
	ID         string
	Pages      []TemplatePage
	FrontCover *TemplateCover
	BackCover  *TemplateCover
	CreatedAt  time.Time
}

type CreateTemplateParams struct {
	ID         string
	Pages      []TemplatePage
	FrontCover *TemplateCover
	BackCover  *TemplateCover
}

type TemplatePage struct {
	ID    string
	Slots []TemplateSlot
}

type TemplateSlot struct {
	ID                  string
	PhotoID             *string
	RequiredOrientation *string
}

type TemplateCover struct {
	Mode    string
	PhotoID *string
	Text    *string
}
