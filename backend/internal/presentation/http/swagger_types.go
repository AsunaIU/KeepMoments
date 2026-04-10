package http

type HealthResponse struct {
	Status string `json:"status" example:"ok"`
}

type ErrorResponse struct {
	Error string `json:"error" example:"invalid json body"`
}

type AuthRequest struct {
	Email    string `json:"email" example:"user@example.com"`
	Password string `json:"password" example:"secret123"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" example:"g1A2b3C4d5E6f7G8"`
}

type CreateTemplateRequest struct {
	Name            string `json:"name" example:"basic"`
	DescriptionJSON string `json:"description_json" example:"{\"layout\":\"story\",\"ratio\":\"9:16\"}"`
}
