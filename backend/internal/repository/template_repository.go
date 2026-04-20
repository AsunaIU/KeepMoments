package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"keepmoments/backend/internal/model"

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
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return model.Template{}, fmt.Errorf("begin create template tx: %w", err)
	}
	defer tx.Rollback(ctx)

	var template model.Template
	err = tx.QueryRow(ctx, `
		INSERT INTO templates (id)
		VALUES ($1)
		RETURNING id, created_at
	`, params.ID).Scan(&template.ID, &template.CreatedAt)
	if err != nil {
		return model.Template{}, fmt.Errorf("insert template: %w", err)
	}

	template.Pages = params.Pages

	for pageIndex, page := range params.Pages {
		_, err = tx.Exec(ctx, `
			INSERT INTO template_pages (template_id, id, page_order)
			VALUES ($1, $2, $3)
		`, params.ID, page.ID, pageIndex)
		if err != nil {
			return model.Template{}, fmt.Errorf("insert template page: %w", err)
		}

		for slotIndex, slot := range page.Slots {
			_, err = tx.Exec(ctx, `
				INSERT INTO template_slots (template_id, page_id, id, photo_id, slot_order)
				VALUES ($1, $2, $3, $4, $5)
			`, params.ID, page.ID, slot.ID, slot.PhotoID, slotIndex)
			if err != nil {
				return model.Template{}, fmt.Errorf("insert template slot: %w", err)
			}
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return model.Template{}, fmt.Errorf("commit create template tx: %w", err)
	}

	return template, nil
}

func (r *TemplateRepository) GetByID(ctx context.Context, id string) (model.Template, error) {
	templates, err := r.queryTemplates(ctx, `
		SELECT
			t.id,
			t.created_at,
			p.id AS page_id,
			s.id AS slot_id,
			s.photo_id
		FROM templates t
		LEFT JOIN template_pages p ON p.template_id = t.id
		LEFT JOIN template_slots s ON s.template_id = t.id AND s.page_id = p.id
		WHERE t.id = $1
		ORDER BY p.page_order ASC, s.slot_order ASC
	`, id)
	if err != nil {
		return model.Template{}, err
	}

	if len(templates) == 0 {
		return model.Template{}, ErrTemplateNotFound
	}

	return templates[0], nil
}

func (r *TemplateRepository) List(ctx context.Context) ([]model.Template, error) {
	return r.queryTemplates(ctx, `
		SELECT
			t.id,
			t.created_at,
			p.id AS page_id,
			s.id AS slot_id,
			s.photo_id
		FROM templates t
		LEFT JOIN template_pages p ON p.template_id = t.id
		LEFT JOIN template_slots s ON s.template_id = t.id AND s.page_id = p.id
		ORDER BY t.created_at DESC, p.page_order ASC, s.slot_order ASC
	`)
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

func (r *TemplateRepository) queryTemplates(ctx context.Context, query string, args ...any) ([]model.Template, error) {
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query templates: %w", err)
	}
	defer rows.Close()

	var templates []model.Template
	templateIndexes := make(map[string]int)
	pageIndexes := make(map[string]map[string]int)

	for rows.Next() {
		var (
			templateID string
			createdAt  sql.NullTime
			pageID     sql.NullString
			slotID     sql.NullString
			photoID    sql.NullString
		)

		if err := rows.Scan(&templateID, &createdAt, &pageID, &slotID, &photoID); err != nil {
			return nil, fmt.Errorf("scan template tree: %w", err)
		}

		templateIndex, exists := templateIndexes[templateID]
		if !exists {
			templates = append(templates, model.Template{
				ID:        templateID,
				CreatedAt: createdAt.Time,
				Pages:     []model.TemplatePage{},
			})
			templateIndex = len(templates) - 1
			templateIndexes[templateID] = templateIndex
			pageIndexes[templateID] = make(map[string]int)
		}

		if !pageID.Valid {
			continue
		}

		pageIndex, exists := pageIndexes[templateID][pageID.String]
		if !exists {
			templates[templateIndex].Pages = append(templates[templateIndex].Pages, model.TemplatePage{
				ID:    pageID.String,
				Slots: []model.TemplateSlot{},
			})
			pageIndex = len(templates[templateIndex].Pages) - 1
			pageIndexes[templateID][pageID.String] = pageIndex
		}

		if !slotID.Valid {
			continue
		}

		var slotPhotoID *string
		if photoID.Valid {
			value := photoID.String
			slotPhotoID = &value
		}

		templates[templateIndex].Pages[pageIndex].Slots = append(templates[templateIndex].Pages[pageIndex].Slots, model.TemplateSlot{
			ID:      slotID.String,
			PhotoID: slotPhotoID,
		})
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate templates: %w", err)
	}

	return templates, nil
}
