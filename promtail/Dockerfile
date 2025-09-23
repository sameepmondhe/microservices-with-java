FROM grafana/promtail:2.9.0

# Copy Promtail configuration
COPY promtail.yaml /etc/promtail/config.yml

# Create necessary directories
RUN mkdir -p /tmp

# Expose port
EXPOSE 9080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9080/ready || exit 1

# Start Promtail
CMD ["-config.file=/etc/promtail/config.yml"]