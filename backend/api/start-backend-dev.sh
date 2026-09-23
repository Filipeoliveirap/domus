#!/bin/bash
export SPRING_PROFILES_ACTIVE=dev
export JWT_SECRET=dev-secret-key-1234567890-domus-dev-key-super-secret
export JWT_EXPIRATION_MS=3600000
export JWT_REFRESH_EXPIRATION_MS=604800000
export PAGAMENTO_ENCRYPTION_KEY=c3VwZXItc2VjcmV0LWtleS1kZXYtMTIzNDU2Nzg5MA==
export DATABASE_URL=jdbc:postgresql://localhost:5432/domus
export DATABASE_USERNAME=domus
export DATABASE_PASSWORD=domus123
export GOOGLE_CLIENT_ID=dev-google-client-id.apps.googleusercontent.com
export MERCADOPAGO_CLIENT_ID=dev-mp-client-id
export MERCADOPAGO_CLIENT_SECRET=dev-mp-client-secret
export MERCADOPAGO_REDIRECT_URI=http://localhost:8080/pagamentos/conta/callback
export MERCADOPAGO_WEBHOOK_SECRET=dev-mp-webhook-secret
export SPRING_FLYWAY_LOCK_RETRY_COUNT=50

./mvnw spring-boot:run
