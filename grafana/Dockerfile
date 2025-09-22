FROM grafana/grafana:latest

# Copy provisioning configuration
COPY provisioning/ /etc/grafana/provisioning/

# Set permissions (grafana user is UID 472 in the official image)
USER root
RUN chown -R 472:472 /etc/grafana/provisioning
USER 472

# Expose port
EXPOSE 3000