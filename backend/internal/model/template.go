package model

import "time"

type Template struct {
	ID        string
	Pages     []TemplatePage
	CreatedAt time.Time
}

type CreateTemplateParams struct {
	ID    string
	Pages []TemplatePage
}

type TemplatePage struct {
	ID    string
	Slots []TemplateSlot
}

type TemplateSlot struct {
	ID      string
	PhotoID *string
}
