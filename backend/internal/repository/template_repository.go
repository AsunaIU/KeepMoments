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

var ErrTemplateNotFound = errors.New("template not found")
var ErrTemplateHasPhotos = errors.New("template has photos")

type TemplateRepository struct {
	pool *pgxpool.Pool
}

func NewTemplateRepository(pool *pgxpool.Pool) *TemplateRepository {
	return &TemplateRepository{pool: pool}
}

func (r *TemplateRepository) Create(ctx context.Context, params model.CreateTemplateParams) (model.Template, error) {
	const query = `
		INSERT INTO templates (id, template_json)
		VALUES ($1, $2)
		RETURNING id, template_json, created_at
	`

	var template model.Template
	err := r.pool.QueryRow(ctx, query, params.ID, params.TemplateJSON).
		Scan(&template.ID, &template.TemplateJSON, &template.CreatedAt)
	if err != nil {
		return model.Template{}, fmt.Errorf("create template: %w", err)
	}

	return template, nil
}

func (r *TemplateRepository) GetByID(ctx context.Context, id string) (model.Template, error) {
	const query = `
		SELECT id, template_json, created_at
		FROM templates
		WHERE id = $1
	`

	var template model.Template
	err := r.pool.QueryRow(ctx, query, id).
		Scan(&template.ID, &template.TemplateJSON, &template.CreatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return model.Template{}, ErrTemplateNotFound
		}

		return model.Template{}, fmt.Errorf("get template: %w", err)
	}

	return template, nil
}

func (r *TemplateRepository) List(ctx context.Context) ([]model.Template, error) {
	const query = `
		SELECT id, template_json, created_at
		FROM templates
		ORDER BY id DESC
	`

	rows, err := r.pool.Query(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("list templates: %w", err)
	}
	defer rows.Close()

	var templates []model.Template
	for rows.Next() {
		var template model.Template
		if err := rows.Scan(&template.ID, &template.TemplateJSON, &template.CreatedAt); err != nil {
			return nil, fmt.Errorf("scan template: %w", err)
		}
		templates = append(templates, template)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate templates: %w", err)
	}

	return templates, nil
}

func (r *TemplateRepository) Delete(ctx context.Context, id string) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM templates WHERE id = $1`, id)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23503" {
			return ErrTemplateHasPhotos
		}

		return fmt.Errorf("delete template: %w", err)
	}

	if tag.RowsAffected() == 0 {
		return ErrTemplateNotFound
	}

	return nil
}
