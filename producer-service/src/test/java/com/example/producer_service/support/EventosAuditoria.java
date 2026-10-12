package com.example.producer_service.support;

/**
 * JSON de ejemplo de un evento de auditoría de ms-bff (esquema v1).
 */
public final class EventosAuditoria {

	private EventosAuditoria() {
	}

	public static String json(String eventId, int status) {

		String outcome = status < 400 ? "SUCCESS" : status < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
		String error = status < 400 ? "null" : "\"HTTP_" + status + "\"";
		return """
				{"schemaVersion":"1.0","eventType":"BFF_HTTP_CALL","eventId":"%s",
				 "timestamp":"2026-10-11T12:00:00.000Z","requestId":"req-%s","sourceService":"ms-bff",
				 "sourceInstance":"ms-bff:10.0.0.5:8080","method":"GET","path":"/api/movil/cuentas/110",
				 "routePattern":"/api/movil/cuentas/{cuentaId}","endpoint":"movil.cuentas","channel":"movil",
				 "user":"ana","authenticated":true,"status":%d,"outcome":"%s","durationMs":42,
				 "errorCode":%s,"errorMessage":null}
				""".formatted(eventId, eventId, status, outcome, error);
	}
}
