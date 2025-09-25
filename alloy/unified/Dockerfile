FROM grafana/alloy:latest

# Copy the unified configuration
COPY alloy-unified.alloy /etc/alloy/config.alloy

# Expose ports for log collection and metrics
EXPOSE 12345 12346

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD alloy fmt /etc/alloy/config.alloy || exit 1

# Run Alloy with the unified configuration
ENTRYPOINT ["/bin/alloy"]
CMD ["run", "/etc/alloy/config.alloy", "--server.http.listen-addr=0.0.0.0:12345"]