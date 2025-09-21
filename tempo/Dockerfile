FROM grafana/tempo:latest

# Copy the tempo configuration
COPY tempo.yaml /etc/tempo.yaml

# Expose the required ports
EXPOSE 3200 4317 4318

# Start tempo with the custom config
CMD ["-config.file=/etc/tempo.yaml"]