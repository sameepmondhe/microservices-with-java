FROM prom/prometheus:latest

# Copy the prometheus configuration
COPY prometheus.yml /etc/prometheus/prometheus.yml

# Expose the Prometheus web UI port
EXPOSE 9090

# Start Prometheus with the custom config
CMD ["--config.file=/etc/prometheus/prometheus.yml", \
     "--storage.tsdb.path=/prometheus", \
     "--web.console.libraries=/etc/prometheus/console_libraries", \
     "--web.console.templates=/etc/prometheus/consoles", \
     "--web.enable-lifecycle"]