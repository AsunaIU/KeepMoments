package model

import "time"

type Template struct {
	ID           string
	TemplateJSON []byte
	CreatedAt    time.Time
}

type CreateTemplateParams struct {
	ID           string
	TemplateJSON []byte
}
