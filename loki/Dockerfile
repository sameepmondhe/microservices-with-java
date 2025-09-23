FROM grafana/loki:2.9.0

# Copy the Loki configuration
COPY loki.yaml /etc/loki/local-config.yaml

# Create necessary directories
RUN mkdir -p /tmp/loki/chunks /tmp/loki/rules

# Expose ports
EXPOSE 3100 9096

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3100/ready || exit 1

# Start Loki
CMD ["-config.file=/etc/loki/local-config.yaml"]