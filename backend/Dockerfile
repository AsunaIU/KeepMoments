FROM golang:1.25 AS builder

WORKDIR /app

COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /keepmoments ./cmd/app

FROM gcr.io/distroless/static-debian12

WORKDIR /app

COPY --from=builder /keepmoments /app/keepmoments
COPY --from=builder /app/db/migrations /app/db/migrations

EXPOSE 8080

ENTRYPOINT ["/app/keepmoments"]
