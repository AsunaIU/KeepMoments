package model

import "time"

type Template struct {
	ID              int64
	Name            string
	DescriptionJSON []byte
	CreatedAt       time.Time
}

type CreateTemplateParams struct {
	Name            string
	DescriptionJSON []byte
}
