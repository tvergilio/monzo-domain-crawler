FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user to run the application
RUN addgroup -S crawler && adduser -S crawler -G crawler

COPY ./build/distributions/*.tar /app/
RUN tar -xvf /app/*.tar --strip-components=1 && rm /app/*.tar

# Create data directory with proper permissions
RUN mkdir -p /app/data && chown -R crawler:crawler /app

# Switch to non-root user
USER crawler

# Set the entry point to run the application
ENTRYPOINT ["bin/monzo-domain-crawler"]