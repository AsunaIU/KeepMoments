package model

import "time"

type User struct {
	ID           int64
	Email        string
	PasswordHash string
	CreatedAt    time.Time
}

type CreateUserParams struct {
	Email        string
	PasswordHash string
}
