package com.capstone.su26_sep490_g2_be.system;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-06 Participant Roster Management.
 * Rows TC-SYS-BF06-001..011 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF06_ParticipantRosterSystemTest extends SystemTestBase {

	/**
	 * TC-SYS-BF06-001..005 — main flow: manual add + real Excel-import (built in-memory with Apache
	 * POI, uploaded as a genuine multipart {@code .xlsx}), roster publicly viewable.
	 */
	@Test
	void mainFlow_manualAndImportedParticipants_publiclyViewable() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createConfigurePublishTournament(managerToken, branchId, 6);

		// TC-SYS-BF06-002 (BF Step 1a)
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
						.content("""
								{"displayName":"QA Manual Player","seedNo":1}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// TC-SYS-BF06-003 (BF Step 1b) — Excel-import preview: real .xlsx multipart upload, 2 valid rows
		byte[] excelBytes = buildParticipantExcelBytes(
				new String[] {"QA Import P1", "0911111111"},
				new String[] {"QA Import P2", "0911111112"});
		MockMultipartFile importFile = new MockMultipartFile("file", "participants.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);
		mvc.perform(multipart("/api/v1/manager/tournaments/{id}/participants/import-excel/preview", tournamentId)
						.file(importFile)
						.header("Authorization", "Bearer " + managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.validCount").value(2))
				.andExpect(jsonPath("$.data.invalidCount").value(0));

		// TC-SYS-BF06-004 (BF Step 1c) — confirm import: 2 more ACTIVE participants persisted
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/import-excel/confirm", tournamentId), managerToken)
						.content("""
								{"rows":[
								 {"name1":"QA Import P1","phone1":"0911111111"},
								 {"name1":"QA Import P2","phone1":"0911111112"}
								]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.imported").value(2))
				.andExpect(jsonPath("$.data.skipped").value(0));

		// TC-SYS-BF06-005 — End condition: roster finalized and publicly viewable, manual + both
		// imported participants all present
		mvc.perform(get("/api/v1/tournaments/{id}/participants", tournamentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.displayName=='QA Manual Player')]").exists())
				.andExpect(jsonPath("$.data[?(@.displayName=='QA Import P1')]").exists())
				.andExpect(jsonPath("$.data[?(@.displayName=='QA Import P2')]").exists());
	}

	/** Builds a minimal real {@code .xlsx} in-memory: header row + one data row per array
	 * (col0=name, col1=phone) — matches the SINGLE-participant-type column layout read by
	 * {@code ParticipantExcelServiceImpl#parseExcelRows}. */
	private byte[] buildParticipantExcelBytes(String[]... dataRows) throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("Người tham gia");
			var header = sheet.createRow(0);
			header.createCell(0).setCellValue("Tên hiển thị");
			header.createCell(1).setCellValue("Số điện thoại");
			header.createCell(2).setCellValue("Hạng");
			for (int i = 0; i < dataRows.length; i++) {
				var row = sheet.createRow(i + 1);
				String[] cols = dataRows[i];
				for (int c = 0; c < cols.length; c++) {
					if (cols[c] != null) {
						row.createCell(c).setCellValue(cols[c]);
					}
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * TC-SYS-BF06-006..009 — exception path: roster locked after draw; add rejected.
	 * No remediation step: {@code DELETE .../draw} / cancelDraw does not exist anywhere in this
	 * codebase (confirmed by grepping the whole controller + service layers) — once DRAW_PREVIEW
	 * is reached, there is no API path back to a roster-editable state short of cancelling the
	 * whole tournament, which does not restore editability either. This narrows the chain from
	 * its original design (which assumed a cancel-draw endpoint existed).
	 */
	@Test
	void exceptionPath_rosterLockedAfterDraw_thenRemediatedByCancellingDraw() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createConfigurePublishTournament(managerToken, branchId, 4);

		for (int i = 1; i <= 4; i++) {
			mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
							.content("""
									{"displayName":"QA Roster Player %s","seedNo":%s}
									""".formatted(i, i)))
					.andExpect(status().isCreated());
		}

		// TC-SYS-BF06-007
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"REGISTRATION_CLOSED"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF06-008
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw", tournamentId), managerToken))
				.andExpect(status().isCreated());

		// TC-SYS-BF06-009 — roster now locked
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
						.content("""
								{"displayName":"QA Rejected Player","seedNo":5}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOURNAMENT_004"));

		// TC-SYS-BF06-009 (verification) — the lock is durable: a second attempt is rejected the
		// same way, confirming this isn't a transient/one-time guard.
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
						.content("""
								{"displayName":"QA Rejected Player 2","seedNo":6}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOURNAMENT_004"));
	}

	private Number createConfigurePublishTournament(String managerToken, Number branchId, int maxParticipants) throws Exception {
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF06 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":%s,"branchId":%s,"isRegister":false,
								 "isShowTournament":true}
								""".formatted(uniq(), maxParticipants, branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(put("/api/v1/manager/tournaments/{id}/config", tournamentId), managerToken)
						.content("""
								{"seedingMethod":"RANK","raceToOverrides":[],"fields":[
								 {"fieldKey":"bracket_size","value":"8"},
								 {"fieldKey":"third_place_match","value":"true"},
								 {"fieldKey":"break_rule","value":"ALTERNATE_BREAK"},
								 {"fieldKey":"lag_for_break","value":"true"}
								]}
								"""))
				.andExpect(status().isOk());

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		return tournamentId;
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
