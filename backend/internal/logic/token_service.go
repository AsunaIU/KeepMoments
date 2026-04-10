package logic

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"keepmoments/backend/internal/config"
	"keepmoments/backend/internal/repository"

	"github.com/golang-jwt/jwt/v5"
)

var (
	ErrInvalidAccessToken  = errors.New("invalid access token")
	ErrInvalidRefreshToken = errors.New("invalid refresh token")
)

type TokenService struct {
	cfg         config.AuthConfig
	refreshRepo *repository.RefreshTokenRepository
}

type AuthTokens struct {
	AccessToken      string    `json:"access_token"`
	RefreshToken     string    `json:"refresh_token"`
	TokenType        string    `json:"token_type"`
	AccessExpiresAt  time.Time `json:"access_expires_at"`
	RefreshExpiresAt time.Time `json:"refresh_expires_at"`
}

type AccessClaims struct {
	UserID int64 `json:"user_id"`
	jwt.RegisteredClaims
}

func NewTokenService(cfg config.AuthConfig, refreshRepo *repository.RefreshTokenRepository) *TokenService {
	return &TokenService{cfg: cfg, refreshRepo: refreshRepo}
}

func (s *TokenService) IssueTokens(ctx context.Context, userID int64) (AuthTokens, error) {
	if err := s.refreshRepo.DeleteExpired(ctx); err != nil {
		return AuthTokens{}, err
	}

	now := time.Now().UTC()
	accessExpiresAt := now.Add(time.Duration(s.cfg.AccessTokenTTLMin) * time.Minute)
	refreshExpiresAt := now.Add(time.Duration(s.cfg.RefreshTokenTTLHour) * time.Hour)

	accessToken, err := s.buildAccessToken(userID, accessExpiresAt)
	if err != nil {
		return AuthTokens{}, err
	}

	refreshToken, err := generateRefreshToken()
	if err != nil {
		return AuthTokens{}, err
	}

	if err := s.refreshRepo.Create(ctx, userID, hashToken(refreshToken), refreshExpiresAt); err != nil {
		return AuthTokens{}, err
	}

	return AuthTokens{
		AccessToken:      accessToken,
		RefreshToken:     refreshToken,
		TokenType:        "Bearer",
		AccessExpiresAt:  accessExpiresAt,
		RefreshExpiresAt: refreshExpiresAt,
	}, nil
}

func (s *TokenService) RefreshTokens(ctx context.Context, refreshToken string) (AuthTokens, error) {
	hashed := hashToken(refreshToken)
	tokenModel, err := s.refreshRepo.Get(ctx, hashed)
	if err != nil {
		if errors.Is(err, repository.ErrRefreshTokenNotFound) {
			return AuthTokens{}, ErrInvalidRefreshToken
		}

		return AuthTokens{}, err
	}

	if time.Now().UTC().After(tokenModel.ExpiresAt) {
		_ = s.refreshRepo.Delete(ctx, hashed)
		return AuthTokens{}, ErrInvalidRefreshToken
	}

	if err := s.refreshRepo.Delete(ctx, hashed); err != nil {
		return AuthTokens{}, err
	}

	return s.IssueTokens(ctx, tokenModel.UserID)
}

func (s *TokenService) ParseAccessToken(tokenString string) (int64, error) {
	token, err := jwt.ParseWithClaims(tokenString, &AccessClaims{}, func(token *jwt.Token) (any, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, ErrInvalidAccessToken
		}

		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil {
		return 0, ErrInvalidAccessToken
	}

	claims, ok := token.Claims.(*AccessClaims)
	if !ok || !token.Valid {
		return 0, ErrInvalidAccessToken
	}

	return claims.UserID, nil
}

func (s *TokenService) buildAccessToken(userID int64, expiresAt time.Time) (string, error) {
	claims := AccessClaims{
		UserID: userID,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   fmt.Sprintf("%d", userID),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			IssuedAt:  jwt.NewNumericDate(time.Now().UTC()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString([]byte(s.cfg.JWTSecret))
	if err != nil {
		return "", fmt.Errorf("sign access token: %w", err)
	}

	return signed, nil
}

func generateRefreshToken() (string, error) {
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("generate refresh token: %w", err)
	}

	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
