# Bracket & Seeding — Thống kê vấn đề và hướng xử lý

> Rà soát ngày 2026-08-04 trên `BracketGenerationServiceImpl` (1659 dòng) và các file liên quan.
> Nguồn: đọc trực tiếp code, không suy đoán. Mọi tham chiếu đều kèm `file:line`.

> ## ⚠️ Cập nhật 2026-08-04 — thể thức GROUP_PLAYOFF đã bị gỡ bỏ hoàn toàn
>
> Thay vì sửa từng lỗi, nhóm quyết định **xoá hẳn thể thức chia vòng bảng**. Đã thực hiện:
> gỡ `TournamentFormat.GROUP_PLAYOFF`, xoá `generateGroupPlayoff()` / `getLeagueStandings()` /
> `populateLeaguePlayoff()` / `eliminateBottomParticipants()`, xoá endpoint `/standings` và
> `/eliminate-bottom`, xoá 9 config field chia bảng, xoá 8 giải GROUP_PLAYOFF khỏi DB.
>
> **Vấn đề [1](#1), [2](#2), [3](#3), [6](#6) không còn tồn tại** — giữ lại trong tài liệu để
> ghi nhận lý do gỡ bỏ. **Vấn đề [8](#8) đã được sửa riêng** (xem mục 8).
> **Vấn đề [4](#4) và [7](#7) đã được xử lý** ở đợt thay ELO bằng hệ hạng cơ thủ Việt Nam.
> **Chỉ còn [5](#5)** vẫn hiệu lực.

---

## 0. Tóm tắt

| # | Vấn đề | Mức độ | Thể thức ảnh hưởng | Trạng thái |
|---|--------|--------|--------------------|-----------|
| [1](#1) | `playoff_size` không phải lũy thừa 2 → `ArrayIndexOutOfBoundsException`, bốc thăm sập | 🔴 Critical | GROUP_PLAYOFF | ✅ Hết — đã gỡ thể thức |
| [2](#2) | Playoff xếp sai nhánh — hạng 1 gặp hạng 2 ở bán kết | 🔴 Critical | GROUP_PLAYOFF | ✅ Hết — đã gỡ thể thức |
| [3](#3) | Hòa điểm vòng tròn → ai vào playoff do thứ tự `HashMap` quyết định | 🟠 High | GROUP_PLAYOFF | ✅ Hết — đã gỡ thể thức |
| [4](#4) | `ELO` là lựa chọn giả — chạy y hệt `MANUAL` | 🟠 High | Tất cả | ✅ Đã sửa — thay bằng `RANK` (hạng cơ thủ VN) |
| [5](#5) | Config seeding là "nút bấm chết" — Owner chỉnh nhưng không có tác dụng | 🟠 High | SE / DE | ❗ Còn (đã bớt, xem mục 5) |
| [6](#6) | GROUP_PLAYOFF không hề chia bảng, nhưng validation vẫn ép `group_count × players_per_group` | 🟠 High | GROUP_PLAYOFF | ✅ Hết — đã gỡ thể thức |
| [7](#7) | `seedNo` thiếu unique DB, không giới hạn trên, không bắt liên tục | 🟡 Medium | Tất cả | ✅ Hết — đã gỡ hẳn `seedNo` + chế độ Thủ công |
| [8](#8) | `swapPlayers` có thể tạo trận PENDING thiếu người → kẹt bracket | 🟡 Medium | SE / DE | ✅ Đã sửa |

**Phần đang hoạt động đúng** (đã kiểm chứng, không cần đụng):
- `standardSeedOrder()` — thuật toán rải nhánh đệ quy, đúng chuẩn quốc tế.
- `assignSeededRound1()` — gán vòng 1 + ưu tiên BYE cho hạt giống cao.
- SINGLE_ELIMINATION, DOUBLE_ELIMINATION (cả 2 biến thể), playoff của PROGRESSIVE_ROUND_ROBIN.

**Còn phải làm**: chỉ còn [#5](#5) — một số config seeding vẫn chưa có tác dụng.

---

## 1. `playoff_size` không phải lũy thừa 2 → crash bốc thăm {#1}  ✅ ĐÃ GỠ

**Vị trí:** `BracketGenerationServiceImpl.java:394-463` (`generateGroupPlayoff`)

**Mô tả**

Mảng được cấp phát theo `playoffSize`, nhưng vòng lặp chạy theo `nextPowerOf2(playoffSize)`:

```java
int playoffSize = readIntConfig(t.getId(), "playoff_size", 4);
playoffSize = Math.min(playoffSize, nextPowerOf2(n));
if (playoffSize < 2) playoffSize = 2;
// ⚠️ KHÔNG có bước ép playoffSize về lũy thừa 2, dù comment dòng 395 nói phải là lũy thừa 2

int pTotalRounds = log2(nextPowerOf2(playoffSize));
Match[][] pGrid = new Match[pTotalRounds + 1][(playoffSize / 2) + 1];   // ← cấp phát theo playoffSize
for (int pr = 1; pr <= pTotalRounds; pr++) {
    int mc = nextPowerOf2(playoffSize) >> pr;                            // ← lặp theo lũy thừa 2
    for (int pos = 1; pos <= mc; pos++) {
        pGrid[pr][pos] = m;                                              // ← nổ ở đây
```

**Case tái hiện**

| `playoff_size` | Kích thước mảng | `pos` chạy tới | Kết quả |
|---|---|---|---|
| 2 | 2 (index 0-1) | 1 | ✅ OK |
| **3** | 2 (index 0-1) | 2 | ❌ **AIOOBE** |
| 4 | 3 (index 0-2) | 2 | ✅ OK |
| **5** | 3 (index 0-2) | 4 | ❌ **AIOOBE** |
| **6** | 4 (index 0-3) | 4 | ❌ **AIOOBE** |
| **7** | 4 (index 0-3) | 4 | ❌ **AIOOBE** |
| 8 | 5 (index 0-4) | 4 | ✅ OK |
| 16 | 9 (index 0-8) | 8 | ✅ OK |

`playoff_size` được khai `min 2, max 16` (`DataInitializer.java:183-194`) nên **3, 5, 6, 7 đều nhập được**. Grep toàn codebase: `playoff_size` **không được validate ở bất kỳ đâu** — chỉ `final_playoff_size` (của PROGRESSIVE) mới có validate, và cũng chỉ kiểm tra là số nguyên.

**Hệ quả:** Owner bấm "Bốc thăm" → 500, transaction rollback, giải kẹt ở `REGISTRATION_CLOSED` không đi tiếp được.

**Solution**

```java
// Ép về lũy thừa 2 NGAY khi đọc, rồi dùng thống nhất một biến cho cả cấp phát lẫn vòng lặp
int playoffSize = nextPowerOf2(Math.max(2, readIntConfig(t.getId(), "playoff_size", 4)));
playoffSize = Math.min(playoffSize, nextPowerOf2(n));

int pTotalRounds = log2(playoffSize);
Match[][] pGrid = new Match[pTotalRounds + 1][(playoffSize / 2) + 1];
for (int pr = 1; pr <= pTotalRounds; pr++) {
    int mc = playoffSize >> pr;          // ← cùng gốc với cấp phát
```

Bổ sung validate ở `OwnerTournamentServiceImpl.validateGroupPlayoffConfig()` để báo lỗi sớm trên wizard thay vì để tới lúc bốc thăm:

```java
if (Integer.bitCount(playoffSize) != 1) {
    errors.add(detail("playoff_size", "Số người vào Playoff phải là lũy thừa của 2 (2, 4, 8, 16)"));
}
if (playoffSize > maxParticipants) {
    errors.add(detail("playoff_size", "Số người vào Playoff không được lớn hơn số người tối đa"));
}
```

Cần sửa cả 3 chỗ đọc `playoff_size`: dòng **394**, **527**, **589** — hiện mỗi chỗ tự đọc và tự xử lý khác nhau.

---

## 2. Playoff xếp sai nhánh — hạng 1 gặp hạng 2 ở bán kết {#2}  ✅ ĐÃ GỠ

**Vị trí:** `BracketGenerationServiceImpl.java:606-618` (`populateLeaguePlayoff`)

**Mô tả**

Hàm này tự cài lại cách ghép cặp thay vì dùng `assignSeededRound1` như mọi nơi khác:

```java
int lo = 0, hi = advancers.size() - 1;
for (Match m : poR1) {                       // poR1 đã sort theo positionNo tăng dần
    Participant p1 = ptcpMap.get(advancers.get(lo).getParticipantId());
    Participant p2 = (lo < hi) ? ptcpMap.get(advancers.get(hi).getParticipantId()) : null;
    m.setPlayer1(p1); m.setPlayer2(p2);
    lo++; if (lo <= hi) hi--;
}
```

Cặp sinh ra đúng (1-8, 2-7, 3-6, 4-5) nhưng **thứ tự đổ vào vị trí sai**:

```
Code hiện tại:  M1: 1v8   M2: 2v7   M3: 3v6   M4: 4v5
Chuẩn quốc tế:  M1: 1v8   M2: 4v5   M3: 2v7   M4: 3v6
```

Nhánh nối theo `pos → (pos+1)/2` (dòng 469), tức **M1 và M2 gặp nhau ở vòng 2**.

**Case tái hiện** — giải 16 người, `playoff_size = 8`, vòng tròn xong:

| Vòng | Code hiện tại | Đúng phải là |
|---|---|---|
| Bán kết 1 | **Hạng 1 vs Hạng 2** | Hạng 1 vs Hạng 4 |
| Bán kết 2 | Hạng 3 vs Hạng 4 | Hạng 2 vs Hạng 3 |
| Chung kết | Hạng 3 hoặc 4 chắc chắn có mặt | Hạng 1 vs Hạng 2 (nếu đúng phong độ) |

**Hệ quả:** Toàn bộ công sức xếp hạng vòng tròn mất ý nghĩa — nhất bảng bị "trừng phạt" vì phải gặp nhì bảng ngay bán kết, còn hạng 3/4 nghiễm nhiên vào chung kết. Đây là lỗi **âm thầm**: không crash, không log, kết quả giải vẫn ra bình thường nhưng sai bản chất.

**Solution** — dùng lại đúng hàm mà `fillProgressivePlayoff` (dòng 870) đang dùng:

```java
int bracketSize = nextPowerOf2(advancers.size());
List<Participant> ordered = advancers.stream()
        .map(a -> ptcpMap.get(a.getParticipantId()))
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));

Match[] r1Grid = new Match[(bracketSize / 2) + 1];
poR1.forEach(m -> r1Grid[m.getPositionNo()] = m);
assignSeededRound1(r1Grid, ordered, bracketSize);   // BYE + rải nhánh chuẩn, xử lý luôn trường hợp lẻ
```

Bonus: `assignSeededRound1` cũng xử lý sẵn BYE khi số người đi tiếp không đủ lũy thừa 2, gọn hơn khối `if (p2 == null)` thủ công hiện tại.

---

## 3. Hòa điểm vòng tròn → ai vào playoff do `HashMap` quyết định {#3}  ✅ ĐÃ GỠ

**Vị trí:** `BracketGenerationServiceImpl.java:504-551` (`getLeagueStandings`)

**Mô tả**

```java
Map<Long, Stats> statsMap = new HashMap<>();          // ← HashMap, không có thứ tự nghiệp vụ
...
List<Map.Entry<Long, Stats>> sorted = new ArrayList<>(statsMap.entrySet());
sorted.sort(Comparator
        .comparingInt(e -> e.getValue().wins()).reversed()
        .thenComparingInt(e -> -(frameDiff))
        .thenComparingInt(e -> -framesWon));          // ← hết tiêu chí
...
.advancesToPlayoff(i < playoffSize)
```

Sau 3 tiêu chí là **không còn gì**. `List.sort` ổn định nên nhóm hòa tuyệt đối giữ nguyên thứ tự của `statsMap.entrySet()` — tức thứ tự **băm của `Long` id**.

**Case tái hiện** — giải 8 người, `playoff_size = 4`, hạng 4 và 5 hòa tuyệt đối:

```
Hạng 4: A — 3 thắng, hiệu số +2, 15 ván thắng
Hạng 5: B — 3 thắng, hiệu số +2, 15 ván thắng
→ Ai vào playoff phụ thuộc hashCode của participant id.
→ Kể cả khi A đã thắng B ở vòng tròn, B vẫn có thể được chọn.
```

**Đối chiếu (tại thời điểm phát hiện):** `computeStageStandings()` (dùng cho PROGRESSIVE) có đủ chuỗi `wins → rackDiff → racksWon → head-to-head → seedNo → id`, trong khi `getLeagueStandings()` chỉ có 3 tiêu chí. Hai bảng xếp hạng nằm cùng một file, một cái đầy đủ một cái thiếu hẳn.

> **Thứ tự chốt cuối (2026-08-05):** `số trận thắng → hiệu số → đối đầu → số ván thắng → id`.
> Đối đầu được đẩy lên **trước** số ván thắng theo yêu cầu nghiệp vụ, và mắt xích `seedNo` đã bị gỡ
> cùng với chính trường đó (xem [#7](#7)). `getLeagueStandings()` biến mất theo GROUP_PLAYOFF.
> FE `MatchesTab.computeStandings` đã viết lại đúng y chuỗi này để bảng xếp hạng hai bên không lệch.

Ngoài ra config `group_tiebreaker_order` (mặc định `HEAD_TO_HEAD,SCORE_DIFF`) **không nơi nào đọc**.

**Solution** — tái sử dụng `resolveHeadToHead()` đã có sẵn (dòng 941):

```java
// 1. Dùng LinkedHashMap để thứ tự nền là deterministic
Map<Long, Stats> statsMap = new LinkedHashMap<>();
participants.stream()
        .sorted(Comparator.comparing(Participant::getId))
        .forEach(p -> statsMap.put(p.getId(), new Stats(0, 0, 0, 0)));

// 2. Sau khi sort 3 tiêu chí chính, gọi head-to-head cho các nhóm hòa
resolveHeadToHead(ids, stat, allGroupMatches, playersMap);
```

Nếu muốn làm triệt để, nên **gộp `getLeagueStandings` và `computeStageStandings` thành một hàm dùng chung** — hiện hai hàm tính cùng một thứ bằng hai bộ luật khác nhau, đây là nguồn gốc của lệch lạc.

Đồng thời hiện thực `group_tiebreaker_order` hoặc gỡ nó khỏi catalog (xem [#5](#5)).

---

## 4. `ELO` là lựa chọn giả {#4}  ✅ ĐÃ SỬA

> **Đã xử lý 2026-08-05 — không chọn (a) cũng không chọn (b) bên dưới, mà thay bằng phương án thứ ba:
> hệ hạng cơ thủ Việt Nam.**
>
> Cả hai giải pháp đề xuất ban đầu đều có nhược điểm: (a) chỉ dời vấn đề sang `MANUAL` — cũng là một
> chế độ hỏng (xem [#7](#7)); (b) dùng điểm tích lũy làm ELO thì participant thêm tay/Excel luôn 0
> điểm nên bị đẩy xuống cuối bảng bất kể trình độ thật.
>
> **Đã làm:** thêm `enums/BilliardRank.java` — 13 bậc `CN, A, B, C, D…L` theo hệ phân hạng bi-a Việt
> Nam, cộng `UNKNOWN`. Thứ tự khai báo chính là thứ tự mạnh→yếu (`ordinal()` = bậc). `SeedingMethod`
> nay chỉ còn **`RANDOM`** và **`RANK`**.
>
> **Nguồn hạng** — 3 luồng cùng đổ về `participants.billiard_rank`:
> cơ thủ tự khai trên hồ sơ (đăng ký online sao chép sang), Owner/Manager chọn khi thêm tay, hoặc
> cột "Hạng" trong Excel (để trống → `UNKNOWN`). Hạng trên participant là **ảnh chụp lúc tạo** — cơ
> thủ đổi hạng sau đó không làm xáo trộn bracket của giải đang chạy.
>
> **Xếp cặp** (`resolveRankOrder`): xáo ngẫu nhiên trước rồi sort ổn định theo bậc hạng → người
> **cùng hạng** vẫn bốc thăm ngẫu nhiên với nhau (trùng hạng là chuyện bình thường, không ràng buộc
> duy nhất), nhưng **giữa các bậc** thì luôn mạnh trước. Nhóm `UNKNOWN` xếp sau toàn bộ nhóm có
> hạng — chọn cách này thay vì rải đều để tránh việc `standardSeedOrder` ghép vị trí 1 với vị trí n
> đẩy hai cơ thủ mạnh gặp nhau ngay vòng 1.
>
> **Kiểm chứng thực tế:** giải 16 người (2 CN, 2 A, 4 B, 8 UNKNOWN) → thứ tự hạt giống ra
> `CN, CN, A, A, B, B, B, B, …` và hai cơ thủ CN nằm ở **hai nửa nhánh đối diện**, chỉ có thể gặp
> nhau ở trận chung kết.
>
> **Ngoài phạm vi:** hạng do cơ thủ tự khai, chưa có khâu duyệt. Chấp nhận được khi chỉ dùng để xếp
> cặp; nếu sau này dùng để **chặn quyền dự giải** (CN không được đánh giải phong trào) thì sẽ bị
> lách ngay.

**Vị trí:** `BracketGenerationServiceImpl.java:1198-1214` (`resolveSeedRankOrder`), `SeedingMethod.java`

**Mô tả**

```java
if (SeedingMethod.RANDOM.name().equals(seedingMethod)) { ... }
// Mọi giá trị KHÁC RANDOM (kể cả ELO) đều rơi vào nhánh dưới — sort theo seedNo
```

`SeedingMethod.ELO` có trong enum, có trong danh sách hợp lệ (`OwnerTournamentServiceImpl.java:56`), Owner chọn được trên wizard — nhưng **hành vi giống hệt `MANUAL`**.

Quan trọng hơn: **hệ thống không có trường điểm ELO nào** trên `users` / `user_profiles` / `player_yearly_summaries` để mà tính.

**Case tái hiện:** Owner chọn ELO, kỳ vọng hệ thống tự xếp hạt giống theo trình độ → thực tế nếu không ai có `seedNo` thì **toàn bộ bị xáo trộn ngẫu nhiên**, y như RANDOM.

**Solution** — chọn 1 trong 2:

**(a) Gỡ ELO (khuyến nghị cho phase hiện tại)** — effort S:
- Bỏ `ELO` khỏi `SeedingMethod`.
- Bỏ khỏi `ALLOWED_SEEDING_METHODS` (`OwnerTournamentServiceImpl.java:56`).
- Migration: `UPDATE tournament_configs SET seeding_method='MANUAL' WHERE seeding_method='ELO'`.

**(b) Hiện thực thật** — effort L: cần thêm nguồn xếp hạng. **Đã có sẵn `player_yearly_summaries.total_points`** và bảng xếp hạng điểm tích lũy (tính từ `tournament_results.points_earned`) — có thể dùng làm "ELO" thay vì xây hệ số ELO đúng nghĩa:

```java
if (SeedingMethod.ELO.name().equals(seedingMethod)) {
    Map<Long, Long> pointsByUser = leaderboardService.pointsByUser(allTimeRange);
    return participants.stream()
            .sorted(Comparator.comparingLong(
                    (Participant p) -> -pointsByUser.getOrDefault(userIdOf(p), 0L)))
            .collect(Collectors.toCollection(ArrayList::new));
}
```

Lưu ý: participant thêm tay/import Excel không có `user_id` (xem `TournamentResultRepository.aggregateLeaderboard` javadoc) → sẽ có điểm 0 và bị xếp cuối. Cần quyết định cách xử lý nhóm này trước khi chọn (b).

---

## 5. Config seeding là "nút bấm chết" {#5}  ✅ ĐÃ DỌN

> **Đã xử lý 2026-08-05.** Nguyên tắc phân loại: **field nói dối về hành vi hệ thống thì gỡ, field
> chỉ mang thông tin thi đấu cho người thì giữ.**
>
> Điểm mấu chốt là "code không đọc" **không đồng nghĩa với vô dụng**. `break_rule`,
> `lag_for_break`, `scoring_unit` mô tả luật chơi mà **trọng tài và cơ thủ áp dụng bằng tay** — hệ
> thống không cần enforce, nó chỉ cần ghi lại và hiển thị đúng. Ngược lại `allow_bye` hay
> `seeding_enabled` hứa hẹn hệ thống sẽ **làm khác đi**, mà thực tế không hề — đây mới là thứ gây
> hại, vì Owner tin là đã chỉnh xong rồi giải chạy ra kiểu khác.

### Đã gỡ hoàn toàn (4 key)

| Config | Lý do gỡ |
|---|---|
| `allow_bye` | BYE luôn được gán tự động khi sĩ số không phải lũy thừa 2 — tắt cờ này không ngăn được gì. Đã cân nhắc hiện thực (chặn bốc thăm nếu sĩ số lẻ) nhưng như vậy là **biến một checkbox thành rào chắn có thể làm hỏng giải đang mở đăng ký** |
| `seeding_enabled` | Trùng chức năng với `seedingMethod = RANDOM` — "tắt hạt giống" chính là bốc ngẫu nhiên. Hai nút cùng điều khiển một thứ luôn dẫn tới trạng thái mâu thuẫn (bật `seeding_enabled` + chọn RANDOM thì tính sao?) |
| `grand_final_bracket_reset` | Bộ sinh nhánh chỉ tạo **đúng một** trận GRAND_FINAL ([BGS:246](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L246)), không có nhánh reset nào để bật/tắt |
| `group_tiebreaker_order` | Thứ tự tie-break hard-code. Tệ hơn: giá trị mặc định cũ `POINTS,RACK_DIFF,RACKS_WON,HEAD_TO_HEAD` **ghi sai thứ tự thật** đã chốt ở [#3](#3) (`HEAD_TO_HEAD` đứng **trước** `RACKS_WON`) — field vừa vô tác dụng vừa cung cấp thông tin sai |

### Gỡ theo từng thể thức (1 key)

`bracket_size` **giữ cho SINGLE_ELIMINATION, gỡ khỏi DOUBLE_ELIMINATION**. Với SE nó có tác dụng
thật — đồng bộ hai chiều với `maxParticipants` kèm clamp theo min/max. Với DE thì cả
`syncBracketSizeFromMaxParticipants` lẫn `syncMaxParticipantsFromBracketSize` đều `return` ngay ở
dòng đầu vì check `SINGLE_ELIMINATION_FORMAT_CODE`, nên Owner giải DE thấy ô nhập, chỉnh được, lưu
được, và **không có tác dụng nào**.

`REMOVED_CONFIG_FIELD_KEYS` không dùng được ở đây vì nó xoá theo `fieldKey` trên **mọi** thể thức —
làm vậy sẽ giết luôn bản SE đang sống. Đã thêm cơ chế thứ hai:

```java
/** Field bị gỡ khỏi MỘT thể thức nhưng vẫn sống ở thể thức khác. */
private static final List<Map.Entry<String, String>> REMOVED_FORMAT_SCOPED_FIELDS =
        List.of(Map.entry("DOUBLE_ELIMINATION", "bracket_size"));
```

kèm 2 phương thức repository mới: `FormatConfigFieldRepository.deleteByFormatCodeAndFieldKey()` và
`TournamentConfigValueRepository.deleteByFieldKeyForFormat()` (JPQL, lọc tournament theo `format`).

Label cũng đổi cho đúng nghĩa: **"Số slot bracket" → "Số người tối đa"**, vì nhánh đấu thật luôn
được tính lại từ số người ACTIVE lúc bốc thăm. FE đổi theo ở 2 trang chi tiết giải.

### Giữ lại — chết về logic nhưng có giá trị hiển thị (3 key)

`break_rule`, `lag_for_break`, `scoring_unit`. Cả 3 đều được FE render qua vòng lặp chung
`configFields.map(...)` ([TournamentDetailPage.jsx:319](../SU26_SEP490_G2_FE/src/pages/shared/tournaments/TournamentDetailPage.jsx#L319));
riêng `break_rule` có thêm một đường vào `ConfigSummary`.

**Vì đây là field để người đọc nên nhãn phải đọc được** — đã Việt hoá toàn bộ nhãn và mô tả:

| Key | Nhãn cũ | Nhãn mới |
|---|---|---|
| `break_rule` | Luật break | Luật giao bóng (break) |
| `lag_for_break` | Lag for break | Đấu lag giành quyền giao bóng |
| `de_mode` | Chế độ Double Elimination | Cách kết thúc giải loại kép |
| `se_phase_size` | Số người vào Last X (SE phase) | Số người vào vòng loại trực tiếp |
| `final_playoff_size` | Số người vào Playoff | Số người vào vòng chung kết |
| `bracket_size` | Số slot bracket | Số người tối đa |

Mô tả cũng viết lại cho người không rành thuật ngữ: mô tả `de_mode` trước đây là
`"FULL_DE: DE đến vô địch | CUT_TO_SE: DE đến cutoff rồi chuyển sang SE"` — Owner không thể hiểu nổi.

Hai field `de_mode` / `se_phase_size` trước đây được tạo riêng trong
`DataInitializer.ensureDEConfigFieldsExist()`, **nằm ngoài** `DatabaseSeedData.configFieldCatalog()`
nên không được cơ chế đồng bộ nhãn chạm tới. Đã dời vào catalog chung và xoá hàm đó.

FE cũng Việt hoá theo, ở 14 file: `Race to N` → **"Đánh tới N ván"**, `Race-to` → **"Số ván thắng"**,
`Config fields` → **"Thông số thi đấu"**, và nhãn pha nhánh đấu (`Knockout`/`Winners`/`Losers`/
`Grand Final`/`Playoff` → *Loại trực tiếp / Nhánh thắng / Nhánh thua / Chung kết lớn / Vòng chung kết*).

### Không đụng tới — yếu nhưng chưa chết

`final_playoff_size` (PRR) là dữ liệu lặp (luôn phải bằng phần tử cuối của
`pe_survivors_per_stage`), nhưng **không trơ**: ngoài validate lúc lưu config, nó còn tham gia
quyết định giải có đủ điều kiện kích hoạt hay không ([Owner:866-878](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/OwnerTournamentServiceImpl.java#L866-L878)).
Gỡ thì phải sửa 3 chỗ — để lại cho đợt khác.

### Kết quả dọn dữ liệu

Chạy 1 lần lúc khởi động, số dòng `tournament_config_values` bị xoá:
`allow_bye` 15, `seeding_enabled` 15, `group_tiebreaker_order` 6, `grand_final_bracket_reset` 1,
`bracket_size` (chỉ giải DE) 1 — tổng 38. `bracket_size` của 14 giải SE **giữ nguyên**.

---

## 6. GROUP_PLAYOFF không chia bảng, nhưng validation vẫn ép công thức chia bảng {#6}  ✅ ĐÃ GỠ

**Vị trí:** `BracketGenerationServiceImpl.java:392-481`, `OwnerTournamentServiceImpl.java:1144-1175`

**Mô tả**

Tên thể thức + config (`group_count`, `players_per_group`) gợi ý chia nhiều bảng A/B/C. Nhưng generator tạo **đúng một** stage:

```java
TournamentStage groupStage = stageRepository.save(TournamentStage.builder()
        .tournament(t).name("Vòng tròn").stageType("GROUP")
        .orderNo(1)...);
// circle method cho TOÀN BỘ n người — không có khái niệm bảng
```

Trong khi đó `validateGroupPlayoffConfig()` **chặn Owner lưu config** nếu không thỏa:

```java
int totalSlots = groupCount * playersPerGroup;
if (totalSlots != maxParticipants) {
    errors.add(detail("group_count", "Số bảng x số người/bảng (" + totalSlots
            + ") phải bằng đúng số người tối đa của giải (" + maxParticipants + ")"));
}
```

**Case tái hiện:** Giải 12 người. Owner buộc phải nhập `group_count = 3`, `players_per_group = 4` mới lưu được — rồi bốc thăm ra **một** bảng vòng tròn 12 người (66 trận), không phải 3 bảng 4 người (18 trận). Chênh lệch khối lượng thi đấu rất lớn.

**Hệ quả:** Ràng buộc phiền phức cho 2 tham số vô tác dụng, đồng thời Owner lập kế hoạch số trận/thời lượng sai hoàn toàn.

**Solution** — chọn 1 trong 2:

**(a) Đổi tên cho khớp thực tế (effort M, khuyến nghị)**
- Đổi `GROUP_PLAYOFF` → nhãn "Vòng tròn tính điểm + Playoff" (giữ nguyên `code` để không vỡ FK).
- Gỡ `group_count`, `players_per_group`, `group_assignment` khỏi catalog của format này.
- Xoá `validateGroupPlayoffConfig()`.
- Giữ `playoff_size` làm tham số duy nhất.

**(b) Hiện thực chia bảng thật (effort L)**
- Thêm cột `group_no` vào `tournament_stages` hoặc tạo mỗi bảng một stage `GROUP` với `orderNo` riêng.
- Phân bảng theo `group_assignment`: RANDOM (xáo) / SEEDED (rắn — seed 1→bảng A, 2→B, 3→C, 4→C, 5→B, 6→A...) / MANUAL.
- `getLeagueStandings` tính riêng từng bảng, lấy top `advance_per_group` mỗi bảng.
- Playoff ghép chéo bảng (nhất A gặp nhì B...).

---

## 7. `seedNo` thiếu ràng buộc {#7}  ✅ ĐÃ GỠ

> **Đã xử lý 2026-08-05 — gỡ hẳn `seedNo` thay vì vá từng lỗ hổng.**
>
> **Bằng chứng cho quyết định gỡ:** rà DB thấy **0/44 giải dùng chế độ Thủ công**, trong khi 165
> participant vẫn mang `seedNo`. Tức là số hạt giống được nhập vào nhưng **chưa từng ảnh hưởng tới
> một cặp đấu nào** (chế độ RANDOM bỏ qua hoàn toàn `seedNo`). Tệ hơn, dữ liệu đã hỏng sẵn: giải
> #27 có **64 người cùng mang "hạt giống 1"**, giải #26 là 16 người, giải #30 là 15 người — đúng
> biểu hiện của việc thiếu unique constraint nêu ở mục (a) bên dưới. Con số vô nghĩa này còn được
> render công khai dưới dạng nhãn "Hạt #1" trên trang chi tiết giải.
>
> **Đã gỡ:** cột `participants.seed_no` và `tournament_configs.seed_count` (DROP COLUMN),
> `SeedingMethod.MANUAL`, ô nhập trên form thêm tay, cột "Hạt giống" trong template Excel/CSV,
> nhãn "Hạt #N" ở 3 màn hình công khai, và `ParticipantServiceImpl.assignSeedNumbers()` (code chết).
>
> **Thay thế:** nhu cầu "xếp cơ thủ mạnh vào các nhánh khác nhau" nay do chế độ
> [`RANK`](#4) đảm nhiệm tự động. Muốn ép một cặp đấu cụ thể thì dùng chức năng **đổi chỗ** ở màn
> Bốc thăm sau khi sinh bracket — khả năng can thiệp thủ công không mất đi.
>
> Còn lại 2 chế độ: **Ngẫu nhiên** và **Theo hạng cơ thủ**.

**Vị trí (trước khi gỡ):** `Participant.java:37-38`, `ParticipantController.java:244-246`, `ParticipantExcelServiceImpl.java:281-343`

**Mô tả** — ba lỗ hổng độc lập:

**7a. Không có unique constraint ở DB.** `participants` chỉ khai `@Index(name = "idx_participants_tournament_status", columnList = "tournament_id, status")` — không có unique trên `(tournament_id, seed_no)`. Chống trùng chỉ bằng code:

```java
if (request.getSeedNo() != null && participantRepository.existsByTournamentIdAndStatusAndSeedNo(
        tournamentId, ParticipantStatus.ACTIVE.getValue(), request.getSeedNo())) {
    throw new BusinessException(ErrorCode.PARTICIPANT_SEED_DUPLICATE);
}
```

Hai request thêm participant cùng lúc cùng `seedNo` đều qua được check → trùng hạt giống. Khi đó `Comparator.comparingInt(Participant::getSeedNo)` hòa → giữ thứ tự truy vấn DB (tùy tiện).

**7b. Không giới hạn trên.** Excel chỉ chặn `< 1` (`ParticipantExcelServiceImpl.java:326`); controller thêm tay **không kiểm tra khoảng nào**. `seedNo = 999` cho giải 8 người vẫn lưu được.

**7c. Không bắt liên tục.** `seedCount` chỉ đếm số lượng:

```java
long seededCount = participants.stream().filter(p -> p.getSeedNo() != null).count();
if (seededCount < config.getSeedCount()) throw ...;
```

Bộ seed `{3, 7, 12, 20}` với `seedCount = 4` vẫn qua. Do `resolveSeedRankOrder` dùng **thứ tự tương đối**, bộ này trở thành hạt giống 1, 2, 3, 4 — Owner đặt `seedNo = 3` cho tay cơ mạnh nhất lại thành hạt giống 1.

**Case tái hiện:**

| Case | Input | Kết quả hiện tại | Kỳ vọng |
|---|---|---|---|
| Trùng seed đồng thời | 2 request `seedNo=1` cùng lúc | Cả 2 lưu thành công | 1 thành công, 1 báo lỗi |
| Seed vượt sĩ số | Giải 8 người, `seedNo=999` | Lưu OK, thành hạt giống 1 | Báo lỗi "vượt quá sĩ số" |
| Seed ngắt quãng | `{3,7,12}`, `seedCount=3` | Qua, thành hạt giống 1,2,3 | Báo lỗi "phải là 1,2,3" |

**Solution**

```java
// 1. Ràng buộc DB — chặn race condition tận gốc
@Table(name = "participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participants_tournament_seed",
                columnNames = {"tournament_id", "seed_no"}),
        indexes = { @Index(name = "idx_participants_tournament_status",
                           columnList = "tournament_id, status") })
```
> ⚠️ MySQL cho phép nhiều `NULL` trong unique index → participant không có `seedNo` vẫn thêm thoải mái. Cần dọn dữ liệu trùng sẵn có trước khi bật (`ddl-auto: update` sẽ fail nếu đang có trùng).

```java
// 2. Chặn khoảng — dùng chung cho cả manual add và Excel import
private void validateSeedNo(Integer seedNo, Tournament t) {
    if (seedNo == null) return;
    int limit = t.getMaxParticipants() != null ? t.getMaxParticipants() : Integer.MAX_VALUE;
    if (seedNo < 1 || seedNo > limit) {
        throw new BusinessException(ErrorCode.PARTICIPANT_SEED_OUT_OF_RANGE);
    }
}

// 3. Bắt liên tục 1..seedCount ở generate()
Set<Integer> seeds = participants.stream()
        .map(Participant::getSeedNo).filter(Objects::nonNull).collect(Collectors.toSet());
for (int i = 1; i <= config.getSeedCount(); i++) {
    if (!seeds.contains(i)) throw new BusinessException(ErrorCode.TOURNAMENT_SEED_NOT_CONTIGUOUS);
}
```

---

## 8. `swapPlayers` có thể tạo trận PENDING thiếu người {#8}  ✅ ĐÃ SỬA

> **Đã xử lý 2026-08-04.** Bốn thay đổi, đã kiểm chứng thực tế trên giải Loại kép 30 người
> (bracket 32, 2 BYE):
> 1. `updateByeStatus()` nhận diện BYE ở **cả hai phía** và luôn dồn người còn lại về `player1` —
>    khớp với cách `assignSeededRound1()` vốn đã làm.
> 2. `swapPlayers()` chặn tráo hai ô đều trống → `TOURNAMENT_011`.
> 3. `SwapPlayersRequest` thêm `@Pattern(regexp = "player1|player2")` cho `slot1`/`slot2`.
> 4. `confirmDraw()` gọi `assertNoEmptyRound1Match()` — chặn chốt bracket có trận vòng 1 rỗng
>    (`TOURNAMENT_012`). Chỉ xét stage đã có người nên không đụng nhánh thua Loại kép và
>    playoff Vòng tròn loại dần (vốn cố ý để trống lúc bốc thăm).
>
> **Cơ chế BYE giữ nguyên 100%** — giải sĩ số lệch lũy thừa 2 vẫn miễn trận đầu cho hạt giống cao
> như cũ. Ngược lại, bản sửa còn giữ được BYE trong tình huống mà code cũ làm mất.

**Vị trí:** `BracketGenerationServiceImpl.java` — `updateByeStatus()`, `swapPlayers()`, `confirmDraw()`

**Mô tả**

`updateByeStatus` chỉ xét một chiều:

```java
if (m.getPlayer1() != null && m.getPlayer2() == null) {
    m.setIsBye(true); m.setStatus(BYE); m.setWinner(m.getPlayer1());
} else {
    m.setIsBye(false); m.setStatus(PENDING); m.setWinner(null);   // ← player1 == null cũng rơi vào đây
}
```

Khi sinh bracket thì `assignSeededRound1` luôn đặt người vào `player1` cho trận BYE (phần tử nhỏ của cặp luôn ≤ `bracketSize/2 < n`), nên trạng thái `player1 == null && player2 != null` **không xảy ra lúc bốc thăm**. Nhưng `swapPlayers` phá vỡ bất biến này.

**Case tái hiện**

```
M5 = trận BYE:      player1 = An,  player2 = null
M7 = trận thường:   player1 = Bình, player2 = Cường

POST /owner/tournaments/{id}/draw/swap
{ "matchId1": 7, "slot1": "player1", "matchId2": 5, "slot2": "player2" }

→ M7.player1 = null, M7.player2 = Cường  → PENDING, thiếu người, không ai đá được
→ M5.player1 = An,   M5.player2 = Bình   → trận thường (đúng)
```

M7 kẹt vĩnh viễn: không phải BYE nên không tự resolve, không đủ 2 người nên không bắt đầu được → giải không thể chuyển `COMPLETED`.

**Lỗ hổng phụ:** `slot1` / `slot2` không được validate (`SwapPlayersRequest` không có `@Pattern`), mà `setSlot` coi **mọi chuỗi khác `"player1"`** là `player2`:

```java
private void setSlot(Match m, String slot, Participant p) {
    if ("player1".equals(slot)) m.setPlayer1(p); else m.setPlayer2(p);
}
```
Gửi `slot1 = "abc"` → ghi đè `player2` mà không báo lỗi.

**Solution**

```java
// 1. updateByeStatus xử lý đối xứng — dồn người còn lại về player1
private void updateByeStatus(Match m) {
    Participant p1 = m.getPlayer1(), p2 = m.getPlayer2();
    if (p1 == null && p2 != null) {          // chuẩn hóa: luôn giữ người ở player1
        m.setPlayer1(p2); m.setPlayer2(null);
        p1 = p2; p2 = null;
    }
    if (p1 != null && p2 == null) {
        m.setIsBye(true); m.setStatus(MatchStatus.BYE.getValue()); m.setWinner(p1);
    } else {
        m.setIsBye(false); m.setStatus(MatchStatus.PENDING.getValue()); m.setWinner(null);
    }
}

// 2. Validate slot ở DTO
@Pattern(regexp = "player1|player2", message = "slot chỉ nhận player1 hoặc player2")
private String slot1;
```

Cân nhắc thêm: chặn hoán đổi khi **cả hai slot đều trống** (không có ý nghĩa), và chặn swap giữa hai stage khác loại — hiện chỉ loại trừ `LOSERS` / `GRAND_FINAL`, nên với GROUP_PLAYOFF vẫn có thể swap một trận vòng tròn với một ô playoff còn trống.

---

## 9. Checklist test sau khi sửa

| # | Case | Kỳ vọng |
|---|------|---------|
| 1 | GROUP_PLAYOFF, `playoff_size` = 3 / 5 / 6 / 7 | Wizard báo lỗi khi lưu; nếu đã lỡ lưu thì bốc thăm tự làm tròn lên lũy thừa 2, không crash |
| 2 | GROUP_PLAYOFF 16 người, `playoff_size` = 8, chạy hết vòng tròn | Bán kết là 1-vs-4 và 2-vs-3, **không phải** 1-vs-2 |
| 3 | Vòng tròn, 2 người hòa tuyệt đối ở vạch cắt playoff | Người thắng đối đầu trực tiếp đi tiếp; chạy lại nhiều lần ra cùng kết quả |
| 4 | Seeding = ELO | Hoặc không còn lựa chọn này, hoặc xếp đúng theo điểm tích lũy |
| 5 | `allow_bye = false`, giải 10 người | Hoặc không tạo BYE, hoặc field đã bị gỡ khỏi wizard |
| 6 | Bốc thăm chế độ **Theo hạng**, giải 16 người có 2 CN | Hai CN nằm ở hai nửa nhánh — chỉ gặp nhau ở chung kết |
| 7 | Bốc lại nhiều lần cùng bộ participant | Thứ tự người **cùng hạng** đổi mỗi lần; thứ tự **giữa các bậc hạng** luôn giữ nguyên |
| 8 | Import Excel 1 dòng có hạng "C", 1 dòng bỏ trống | Lần lượt ra `C` và `UNKNOWN`; xác nhận preview không làm mất hạng |
| 9 | Swap slot thật với slot trống của trận BYE | Không sinh trận PENDING thiếu người |
| 10 | Swap với `slot1 = "abc"` | HTTP 400, không ghi đè `player2` |
| 11 | SINGLE_ELIM 8 người không bật `third_place_match`, đá hết, chuyển COMPLETED | Không lỗi unique `(tournament_id, final_rank)` — hạng rải 3,4 và 5,6,7,8 |

---

## 10. Thứ tự sửa đề xuất

> **Cập nhật 2026-08-05 — đợt 1 → 4 đã xong.** Ghi lại nguyên văn để đối chiếu; phần còn lại nằm ở cuối mục.

**~~Đợt 1~~ — chặn máu (effort S, gộp 1 PR):** [#1](#1) + [#2](#2) + [#8](#8) — ✅
[#1](#1) và [#2](#2) biến mất cùng thể thức GROUP_PLAYOFF; [#8](#8) sửa bằng `updateByeStatus()` đối xứng.

**~~Đợt 2~~ — công bằng thi đấu (effort M):** [#3](#3) — ✅
Thứ tự chốt cuối: `số trận thắng → hiệu số → đối đầu → số ván thắng`. FE `computeStandings` đã đồng bộ y hệt BE.

**~~Đợt 3~~ — dọn nợ cấu hình:** [#4](#4) + [#6](#6) — ✅, [#5](#5) — còn một phần
ELO thay bằng hệ hạng bi-a VN; GROUP_PLAYOFF gỡ hẳn, kéo theo 4/8 config chết tự biến mất.

**~~Đợt 4~~ — siết dữ liệu:** [#7](#7) — ✅ nhưng **không cần migration** như dự tính.
Thay vì bật unique constraint rồi dọn dữ liệu trùng, đã gỡ hẳn `seedNo`: `DROP COLUMN` là đủ, dữ liệu trùng theo đó biến mất luôn.

**Việc còn lại — [#5](#5), 4 key:** `allow_bye` (hiện thực), `seeding_enabled` (gỡ),
`group_tiebreaker_order` (ít nhất sửa giá trị mặc định cho khớp code), `bracket_size` (đổi nhãn).
Cần chốt nghiệp vụ trước khi code vì đụng cả catalog lẫn wizard FE.
