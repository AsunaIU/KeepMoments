package logic

import (
	"context"
	"errors"
	"strings"

	"keepmoments/backend/internal/model"
	"keepmoments/backend/internal/repository"

	"golang.org/x/crypto/bcrypt"
)

var ErrInvalidCredentialsInput = errors.New("email and password are required")

type UserRepository interface {
	Create(ctx context.Context, params model.CreateUserParams) (model.User, error)
	GetByEmail(ctx context.Context, email string) (model.User, error)
	GetByID(ctx context.Context, id int64) (model.User, error)
}

type AuthService struct {
	users  *repository.UserRepository
	tokens *TokenService
}

type RegisterInput struct {
	Email    string
	Password string
}

type LoginInput struct {
	Email    string
	Password string
}

type AuthResponse struct {
	UserID int64  `json:"user_id"`
	Email  string `json:"email"`
	AuthTokens
}

func NewAuthService(users *repository.UserRepository, tokens *TokenService) *AuthService {
	return &AuthService{
		users:  users,
		tokens: tokens,
	}
}

func (s *AuthService) Register(ctx context.Context, input RegisterInput) (AuthResponse, error) {
	email := strings.TrimSpace(strings.ToLower(input.Email))
	if email == "" || strings.TrimSpace(input.Password) == "" {
		return AuthResponse{}, ErrInvalidCredentialsInput
	}

	passwordHash, err := hashPassword(input.Password)
	if err != nil {
		return AuthResponse{}, err
	}

	user, err := s.users.Create(ctx, model.CreateUserParams{
		Email:        email,
		PasswordHash: passwordHash,
	})
	if err != nil {
		return AuthResponse{}, err
	}

	tokens, err := s.tokens.IssueTokens(ctx, user.ID)
	if err != nil {
		return AuthResponse{}, err
	}

	return AuthResponse{
		UserID:     user.ID,
		Email:      user.Email,
		AuthTokens: tokens,
	}, nil
}

func (s *AuthService) Login(ctx context.Context, input LoginInput) (AuthResponse, error) {
	email := strings.TrimSpace(strings.ToLower(input.Email))
	if email == "" || strings.TrimSpace(input.Password) == "" {
		return AuthResponse{}, ErrInvalidCredentialsInput
	}

	user, err := s.users.GetByEmail(ctx, email)
	if err != nil {
		if errors.Is(err, repository.ErrUserNotFound) {
			return AuthResponse{}, repository.ErrInvalidCredentials
		}

		return AuthResponse{}, err
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(input.Password)); err != nil {
		return AuthResponse{}, repository.ErrInvalidCredentials
	}

	tokens, err := s.tokens.IssueTokens(ctx, user.ID)
	if err != nil {
		return AuthResponse{}, err
	}

	return AuthResponse{
		UserID:     user.ID,
		Email:      user.Email,
		AuthTokens: tokens,
	}, nil
}

func (s *AuthService) Refresh(ctx context.Context, refreshToken string) (AuthResponse, error) {
	if strings.TrimSpace(refreshToken) == "" {
		return AuthResponse{}, ErrInvalidRefreshToken
	}

	tokens, err := s.tokens.RefreshTokens(ctx, refreshToken)
	if err != nil {
		return AuthResponse{}, err
	}

	userID, err := s.tokens.ParseAccessToken(tokens.AccessToken)
	if err != nil {
		return AuthResponse{}, err
	}

	user, err := s.users.GetByID(ctx, userID)
	if err != nil {
		return AuthResponse{}, err
	}

	return AuthResponse{
		UserID:     user.ID,
		Email:      user.Email,
		AuthTokens: tokens,
	}, nil
}

func hashPassword(password string) (string, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}

	return string(hash), nil
}
