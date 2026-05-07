package model

import "time"

type Photo struct {
	ID              int64
	TemplateID      string
	FileName        string
	ContentType     string
	ObjectKey       string
	DescriptionJSON []byte
	CreatedAt       time.Time
}

type CreatePhotoParams struct {
	TemplateID      string
	FileName        string
	ContentType     string
	ObjectKey       string
	DescriptionJSON []byte
}
