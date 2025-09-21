FROM otel/opentelemetry-collector-contrib:0.109.0

# Copy the collector configuration
COPY collector-config.yaml /etc/otelcol/config.yaml

# Expose the required ports
EXPOSE 4317 4318 13133

# Start the collector with the custom config
CMD ["--config", "/etc/otelcol/config.yaml"]