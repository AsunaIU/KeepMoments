package config

import (
	"fmt"
	"os"
)

type Config struct {
	AppEnv   string
	HTTPPort string
	Postgres PostgresConfig
	S3       S3Config
	Auth     AuthConfig
}

type PostgresConfig struct {
	Host     string
	Port     string
	Database string
	User     string
	Password string
	SSLMode  string
}

type S3Config struct {
	Endpoint  string
	Region    string
	Bucket    string
	AccessKey string
	SecretKey string
	PathStyle bool
}

type AuthConfig struct {
	JWTSecret           string
	AccessTokenTTLMin   int
	RefreshTokenTTLHour int
}

func Load() (Config, error) {
	cfg := Config{
		AppEnv:   getEnv("APP_ENV", "local"),
		HTTPPort: getEnv("HTTP_PORT", "8080"),
		Postgres: PostgresConfig{
			Host:     getEnv("POSTGRES_HOST", "localhost"),
			Port:     getEnv("POSTGRES_PORT", "5432"),
			Database: getEnv("POSTGRES_DB", "keepmoments"),
			User:     getEnv("POSTGRES_USER", "keepmoments"),
			Password: getEnv("POSTGRES_PASSWORD", "keepmoments"),
			SSLMode:  getEnv("POSTGRES_SSLMODE", "disable"),
		},
		S3: S3Config{
			Endpoint:  getEnv("S3_ENDPOINT", "http://localhost:9000"),
			Region:    getEnv("S3_REGION", "us-east-1"),
			Bucket:    getEnv("S3_BUCKET", "keepmoments"),
			AccessKey: getEnv("S3_ACCESS_KEY", "minioadmin"),
			SecretKey: getEnv("S3_SECRET_KEY", "minioadmin"),
			PathStyle: getEnv("S3_USE_PATH_STYLE", "true") == "true",
		},
		Auth: AuthConfig{
			JWTSecret:           getEnv("AUTH_JWT_SECRET", "super-secret-change-me"),
			AccessTokenTTLMin:   getEnvAsInt("AUTH_ACCESS_TOKEN_TTL_MINUTES", 15),
			RefreshTokenTTLHour: getEnvAsInt("AUTH_REFRESH_TOKEN_TTL_HOURS", 720),
		},
	}

	return cfg, cfg.Validate()
}

func (p PostgresConfig) ConnectionString() string {
	return fmt.Sprintf(
		"postgres://%s:%s@%s:%s/%s?sslmode=%s",
		p.User,
		p.Password,
		p.Host,
		p.Port,
		p.Database,
		p.SSLMode,
	)
}

func (c Config) Validate() error {
	if c.HTTPPort == "" {
		return fmt.Errorf("HTTP_PORT is required")
	}

	if c.Postgres.Host == "" || c.Postgres.Database == "" || c.Postgres.User == "" {
		return fmt.Errorf("postgres config is incomplete")
	}

	if c.S3.Endpoint == "" || c.S3.Bucket == "" {
		return fmt.Errorf("s3 config is incomplete")
	}

	if c.Auth.JWTSecret == "" {
		return fmt.Errorf("auth config is incomplete")
	}

	return nil
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}

	return fallback
}

func getEnvAsInt(key string, fallback int) int {
	value := getEnv(key, "")
	if value == "" {
		return fallback
	}

	var parsed int
	_, err := fmt.Sscanf(value, "%d", &parsed)
	if err != nil || parsed <= 0 {
		return fallback
	}

	return parsed
}
