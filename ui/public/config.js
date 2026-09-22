// Browser-facing URLs. With docker compose / kubectl port-forward these are all on localhost.
window.FINFLOW = {
  API: 'http://localhost:8080',
  KC: 'http://localhost:8180/realms/finflow/protocol/openid-connect',
  CLIENT_ID: 'finflow-ui',
  LINKS: {
    grafana: 'http://localhost:3001',
    kafka: 'http://localhost:8090',
    keycloak: 'http://localhost:8180',
    prometheus: 'http://localhost:9090'
  }
};
