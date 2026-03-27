package repository

import (
	"context"
	"errors"
	"fmt"

	"keepmoments/backend/internal/model"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	ErrPhotoNotFound     = errors.New("photo not found")
	ErrTemplateReference = errors.New("template reference not found")
)

type PhotoRepository struct {
	pool *pgxpool.Pool
}

func NewPhotoRepository(pool *pgxpool.Pool) *PhotoRepository {
	return &PhotoRepository{pool: pool}
}

func (r *PhotoRepository) Create(ctx context.Context, params model.CreatePhotoParams) (model.Photo, error) {
	const query = `
		INSERT INTO photos (template_id, file_name, content_type, object_key, description_json)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, template_id, file_name, content_type, object_key, description_json, created_at
	`

	var photo model.Photo
	err := r.pool.QueryRow(
		ctx,
		query,
		params.TemplateID,
		params.FileName,
		params.ContentType,
		params.ObjectKey,
		params.DescriptionJSON,
	).Scan(
		&photo.ID,
		&photo.TemplateID,
		&photo.FileName,
		&photo.ContentType,
		&photo.ObjectKey,
		&photo.DescriptionJSON,
		&photo.CreatedAt,
	)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23503" {
			return model.Photo{}, ErrTemplateReference
		}

		return model.Photo{}, fmt.Errorf("create photo: %w", err)
	}

	return photo, nil
}

func (r *PhotoRepository) GetByID(ctx context.Context, id int64) (model.Photo, error) {
	const query = `
		SELECT id, template_id, file_name, content_type, object_key, description_json, created_at
		FROM photos
		WHERE id = $1
	`

	var photo model.Photo
	err := r.pool.QueryRow(ctx, query, id).
		Scan(&photo.ID, &photo.TemplateID, &photo.FileName, &photo.ContentType, &photo.ObjectKey, &photo.DescriptionJSON, &photo.CreatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.Photo{}, ErrPhotoNotFound
		}

		return model.Photo{}, fmt.Errorf("get photo: %w", err)
	}

	return photo, nil
}

func (r *PhotoRepository) Delete(ctx context.Context, id int64) (model.Photo, error) {
	const query = `
		DELETE FROM photos
		WHERE id = $1
		RETURNING id, template_id, file_name, content_type, object_key, description_json, created_at
	`

	var photo model.Photo
	err := r.pool.QueryRow(ctx, query, id).
		Scan(&photo.ID, &photo.TemplateID, &photo.FileName, &photo.ContentType, &photo.ObjectKey, &photo.DescriptionJSON, &photo.CreatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.Photo{}, ErrPhotoNotFound
		}

		return model.Photo{}, fmt.Errorf("delete photo: %w", err)
	}

	return photo, nil
}
