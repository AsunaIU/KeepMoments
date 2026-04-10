package logic

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"keepmoments/backend/internal/model"
)

type PhotoRepository interface {
	Create(ctx context.Context, params model.CreatePhotoParams) (model.Photo, error)
	GetByID(ctx context.Context, id int64) (model.Photo, error)
	Delete(ctx context.Context, id int64) (model.Photo, error)
}

type ObjectStorage interface {
	PutObject(ctx context.Context, key string, body []byte, contentType string) error
	GetObject(ctx context.Context, key string) ([]byte, error)
	DeleteObject(ctx context.Context, key string) error
}

type PhotoService struct {
	repo    PhotoRepository
	storage ObjectStorage
}

func NewPhotoService(repo PhotoRepository, storage ObjectStorage) *PhotoService {
	return &PhotoService{
		repo:    repo,
		storage: storage,
	}
}

type CreatePhotoInput struct {
	TemplateID      int64
	FileName        string
	ContentType     string
	FileBytes       []byte
	DescriptionJSON json.RawMessage
}

type PhotoDetails struct {
	ID              int64           `json:"id"`
	TemplateID      int64           `json:"template_id"`
	FileName        string          `json:"file_name"`
	ContentType     string          `json:"content_type"`
	ObjectKey       string          `json:"object_key"`
	DescriptionJSON json.RawMessage `json:"description_json" swaggertype:"string" example:"{\"camera\":\"iphone\",\"tags\":[\"travel\"]}"`
	CreatedAt       time.Time       `json:"created_at"`
}

func (s *PhotoService) Create(ctx context.Context, input CreatePhotoInput) (PhotoDetails, error) {
	extension := filepath.Ext(input.FileName)
	objectKey := fmt.Sprintf("photos/%d%s", time.Now().UnixNano(), extension)

	if err := s.storage.PutObject(ctx, objectKey, input.FileBytes, input.ContentType); err != nil {
		return PhotoDetails{}, err
	}

	photo, err := s.repo.Create(ctx, model.CreatePhotoParams{
		TemplateID:      input.TemplateID,
		FileName:        input.FileName,
		ContentType:     input.ContentType,
		ObjectKey:       objectKey,
		DescriptionJSON: []byte(input.DescriptionJSON),
	})
	if err != nil {
		_ = s.storage.DeleteObject(ctx, objectKey)
		return PhotoDetails{}, err
	}

	return mapPhoto(photo), nil
}

func (s *PhotoService) Get(ctx context.Context, id int64) (PhotoDetails, error) {
	photo, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return PhotoDetails{}, err
	}

	return mapPhoto(photo), nil
}

func (s *PhotoService) Delete(ctx context.Context, id int64) error {
	photo, err := s.repo.Delete(ctx, id)
	if err != nil {
		return err
	}

	if err := s.storage.DeleteObject(ctx, photo.ObjectKey); err != nil {
		return err
	}

	return nil
}

func mapPhoto(photo model.Photo) PhotoDetails {
	contentType := photo.ContentType
	if strings.TrimSpace(contentType) == "" {
		contentType = "application/octet-stream"
	}

	return PhotoDetails{
		ID:              photo.ID,
		TemplateID:      photo.TemplateID,
		FileName:        photo.FileName,
		ContentType:     contentType,
		ObjectKey:       photo.ObjectKey,
		DescriptionJSON: json.RawMessage(photo.DescriptionJSON),
		CreatedAt:       photo.CreatedAt,
	}
}
