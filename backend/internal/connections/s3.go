package connections

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"strings"

	"keepmoments/backend/internal/config"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

type S3 struct {
	Client  *s3.Client
	Bucket  string
	Storage *ObjectStorage
}

type ObjectStorage struct {
	client *s3.Client
	bucket string
}

func NewS3(ctx context.Context, cfg config.S3Config) (*S3, error) {
	awsCfg, err := awsconfig.LoadDefaultConfig(
		ctx,
		awsconfig.WithRegion(cfg.Region),
		awsconfig.WithCredentialsProvider(
			credentials.NewStaticCredentialsProvider(cfg.AccessKey, cfg.SecretKey, ""),
		),
	)
	if err != nil {
		return nil, fmt.Errorf("load aws config: %w", err)
	}

	client := s3.NewFromConfig(awsCfg, func(o *s3.Options) {
		o.UsePathStyle = cfg.PathStyle
		o.BaseEndpoint = &cfg.Endpoint
	})

	storage := &ObjectStorage{
		client: client,
		bucket: cfg.Bucket,
	}

	return &S3{
		Client:  client,
		Bucket:  cfg.Bucket,
		Storage: storage,
	}, nil
}

func (s *S3) EnsureBucket(ctx context.Context) error {
	_, err := s.Client.HeadBucket(ctx, &s3.HeadBucketInput{Bucket: &s.Bucket})
	if err == nil {
		return nil
	}

	_, err = s.Client.CreateBucket(ctx, &s3.CreateBucketInput{Bucket: &s.Bucket})
	if err != nil && !strings.Contains(strings.ToLower(err.Error()), "already") {
		return fmt.Errorf("create bucket: %w", err)
	}

	return nil
}

func (s *ObjectStorage) PutObject(ctx context.Context, key string, body []byte, contentType string) error {
	_, err := s.client.PutObject(ctx, &s3.PutObjectInput{
		Bucket:      &s.bucket,
		Key:         &key,
		Body:        bytes.NewReader(body),
		ContentType: &contentType,
	})
	if err != nil {
		return fmt.Errorf("put object: %w", err)
	}

	return nil
}

func (s *ObjectStorage) GetObject(ctx context.Context, key string) ([]byte, error) {
	out, err := s.client.GetObject(ctx, &s3.GetObjectInput{
		Bucket: &s.bucket,
		Key:    &key,
	})
	if err != nil {
		return nil, fmt.Errorf("get object: %w", err)
	}
	defer out.Body.Close()

	payload, err := io.ReadAll(out.Body)
	if err != nil {
		return nil, fmt.Errorf("read object: %w", err)
	}

	return payload, nil
}

func (s *ObjectStorage) DeleteObject(ctx context.Context, key string) error {
	_, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: &s.bucket,
		Key:    &key,
	})
	if err != nil {
		return fmt.Errorf("delete object: %w", err)
	}

	return nil
}
