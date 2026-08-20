# Thuật toán bốc thăm & xếp hạng — Hệ thống quản lý giải đấu Bi-a

> Tài liệu giải thích **dễ hiểu, có ví dụ cụ thể từng bước** cho logic sinh bracket (bốc thăm), xếp hạt giống và tính bảng xếp hạng của từng thể thức thi đấu trong hệ thống. Nội dung rút ra trực tiếp từ mã nguồn (`BracketGenerationServiceImpl`, `TournamentResultServiceImpl`) và đã kiểm thử thực tế trên giải chạy thật.

## Mục lục

1. [Tổng quan các thể thức](#1-tổng-quan-các-thể-thức)
2. [Thuật toán xếp hạt giống chung](#2-thuật-toán-xếp-hạt-giống-chung)
3. [SINGLE_ELIMINATION — Loại trực tiếp](#3-single_elimination--loại-trực-tiếp)
4. [DOUBLE_ELIMINATION — Loại kép (CUT_TO_SE)](#4-double_elimination--loại-kép-cut_to_se)
5. [PROGRESSIVE_ROUND_ROBIN — Vòng tròn loại dần](#5-progressive_round_robin--vòng-tròn-loại-dần)
6. [GROUP_PLAYOFF — ghi chú](#6-group_playoff--ghi-chú)
7. [Thuật toán sinh trận đấu (bracket generation) — cách tạo cây `Match` trong DB](#7-thuật-toán-sinh-trận-đấu-bracket-generation--cách-tạo-cây-match-trong-db)
8. [Bảng so sánh tổng hợp](#8-bảng-so-sánh-tổng-hợp)

---

## 1. Tổng quan các thể thức

| Mã thể thức | Tên hiển thị | Trạng thái |
|---|---|---|
| `SINGLE_ELIMINATION` | Loại trực tiếp (1 lần thua) | Hoạt động đầy đủ |
| `DOUBLE_ELIMINATION` | Loại kép (2 lần thua) | Hoạt động đầy đủ — luôn ở dạng **CUT_TO_SE** (cắt về loại trực tiếp) |
| `PROGRESSIVE_ROUND_ROBIN` | Vòng tròn loại dần + Playoff | Hoạt động đầy đủ |
| `GROUP_PLAYOFF` | Vòng bảng + Playoff | **Chưa có logic riêng** — xem mục 6 |

Cả 3 thể thức hoạt động đều đi qua chung **1 cửa xử lý** khi Owner bấm "Bốc thăm" (`BracketGenerationServiceImpl.generate()`):

```
Bước 1:  Lấy danh sách người tham gia đang ACTIVE của giải
Bước 2:  Đọc xem giải đang chọn xếp hạt giống kiểu gì (RANDOM / RANK / SEED)
Bước 3:  Sắp toàn bộ người tham gia thành 1 danh sách có THỨ TỰ MẠNH-YẾU (rank 1..N)
Bước 4:  Tuỳ theo thể thức mà đưa danh sách đã sắp đó vào 1 trong 3 "khuôn" khác nhau:
            DOUBLE_ELIMINATION      → khuôn Loại kép
            PROGRESSIVE_ROUND_ROBIN → khuôn Vòng tròn
            (còn lại, mặc định)     → khuôn Loại trực tiếp
```

Nói ngắn gọn: **Bước 3 (xếp thứ tự mạnh-yếu) là phần dùng chung** cho cả 3 thể thức, còn cách "xếp vào bracket" thì mỗi thể thức làm 1 kiểu khác nhau. Mục 2 dưới đây giải thích kỹ Bước 3, sau đó mục 3–5 giải thích Bước 4 của từng thể thức.

---

## 2. Thuật toán xếp hạt giống chung

### 2.1 Tại sao cần xếp hạt giống — ví dụ trực quan

Tưởng tượng giải có 8 người, trong đó **Người A** và **Người B** là 2 cao thủ mạnh nhất giải. Nếu bốc thăm hoàn toàn bằng cách rút thăm ngẫu nhiên (không tính toán gì), xác suất A và B rơi vào cùng 1 cặp đấu **ngay vòng 1** là có thật — và nếu điều đó xảy ra, một trong hai người mạnh nhất giải bị loại ngay từ đầu, trong khi 6 người còn lại yếu hơn lại được đi tiếp. Khán giả mất cơ hội xem trận chung kết đáng xem nhất (A vs B), và giải đấu bị coi là "hên xui" chứ không phản ánh đúng thực lực.

**Cách giải quyết**: xếp hạt giống — tính toán trước để A và B chỉ có thể gặp nhau ở trận **cuối cùng** (chung kết) nếu cả hai đều thắng hết các trận trước đó. Đây là kỹ thuật chuẩn mọi giải thể thao chuyên nghiệp đều dùng (banh, cờ, tennis, esports...).

**Mục tiêu cụ thể** mà thuật toán phải đảm bảo:

- Hạt giống 1 & 2 (2 người mạnh nhất) → chỉ gặp nhau được ở **chung kết**
- Hạt giống 1, 2, 3, 4 (4 người mạnh nhất) → không ai trong 4 người này gặp nhau trước **bán kết**
- Cứ thế nhân đôi lên: nhóm 8 người mạnh nhất không gặp nhau trước tứ kết, nhóm 16 người mạnh nhất không gặp nhau trước vòng 1/8...

### 2.2 Ba cách tạo ra "danh sách mạnh-yếu" (`seedingMethod`)

Trước khi xếp vào bracket, hệ thống cần 1 danh sách N người đã có thứ tự rõ ràng: ai là rank 1 (mạnh nhất), ai là rank 2... Có 3 cách tạo danh sách này, Owner chọn 1 khi cấu hình giải:

| Phương thức | Cách tạo thứ tự | Ví dụ |
|---|---|---|
| `RANDOM` | Xáo bài hoàn toàn ngẫu nhiên, không quan tâm ai mạnh ai yếu | Rút thăm kiểu truyền thống |
| `RANK` | Xếp theo hạng cơ thủ đã khai báo trong hồ sơ (CN → A → B → … → L). Cùng hạng thì bốc ngẫu nhiên với nhau. Ai **chưa khai hạng** bị xếp xuống **cuối** toàn bộ danh sách | 2 CN xếp trước, rồi tới A, B... người chưa rõ trình xếp cuối |
| `SEED` | BQT **tự tay gõ số hạt giống** cho từng người (1 = mạnh nhất). Ai **chưa được gõ số** bị xếp xuống cuối, xáo ngẫu nhiên trong nhóm đó | Cho phép chỉ seed vài người mạnh đã biết trước, còn lại bốc tự do |

**Vì sao nhóm "chưa xếp hạng" / "chưa có seed" bắt buộc phải nằm CUỐI danh sách, không được trộn ngẫu nhiên xen vào giữa?**

Vì bước xếp vào bracket ở mục 2.3 chỉ hoạt động đúng nếu các hạt giống thật sự nằm ở những **vị trí đầu danh sách** (rank 1, 2, 3...). Nếu một người "chưa xếp hạng" bị xáo trộn lọt vào vị trí rank 2 (thay vì hạt giống thật), thì hạt giống thật bị đẩy lùi xuống rank 3, 4... và có thể vô tình rơi vào cùng nhánh với hạt giống 1 — mất tác dụng tách nhánh. Đội phát triển từng tái hiện đúng lỗi này: giải 16 người gồm 2 người hạng CN, 2 người hạng A, 4 người hạng B, và 8 người chưa khai hạng — nếu trộn ngẫu nhiên cả nhóm chưa khai hạng vào chung thì ra kết quả 1 người CN gặp 1 người A ngay từ vòng 1, dù đúng ra 2 người mạnh nhất giải này phải được tách xa nhau nhất.

### 2.3 Xếp danh sách vào bracket — thuật toán `standardSeedOrder()`

Đây là phần "lõi" — nhận vào 1 danh sách N người đã có thứ tự rank 1..N, trả về thứ tự nên xếp vào N ô (slot) của bracket.

**Ý tưởng đơn giản nhất có thể diễn đạt bằng lời:** ghép rank 1 (mạnh nhất) với rank N (yếu nhất/cuối danh sách), ghép rank 2 với rank N-1, ghép rank 3 với rank N-2... Nhưng chỉ ghép kiểu "1 đấu N" ở **vòng 1** thì chưa đủ để đảm bảo rank 1 và rank 2 không gặp nhau ở vòng 2 hay vòng 3. Vì vậy thuật toán phải làm việc này **đệ quy** (lặp lại theo từng cấp nhân đôi), không chỉ 1 lần.

```java
private static List<Integer> standardSeedOrder(int size) {
    if (size == 1) return List.of(1);
    List<Integer> prev = standardSeedOrder(size / 2);   // Bước 1: giải bài toán cho bracket nhỏ hơn 1 nửa
    List<Integer> result = new ArrayList<>(size);
    for (int s : prev) {
        result.add(s);
        result.add(size + 1 - s);   // Bước 2: mỗi hạt giống trong bài toán nhỏ được "nhân bản" thêm 1 người bù
    }
    return result;
}
```

**Đọc theo cách dễ hình dung:** để giải bài toán "xếp N người", trước tiên **giả sử đã có sẵn lời giải cho bài toán N/2 người** (tự gọi lại chính mình với kích thước nhỏ hơn), rồi "nhân đôi" lời giải đó lên bằng cách: với mỗi vị trí `s` trong lời giải cũ, chèn thêm ngay sau nó 1 người mới có rank `size + 1 - s` (tức người "đối xứng" ở đầu kia của danh sách). Nhờ vậy 2 rank trong bất kỳ cặp đấu vòng 1 nào cũng luôn **cộng lại đúng bằng `size + 1`** — rank càng nhỏ (càng mạnh) luôn bị ghép với rank càng lớn (càng yếu), không bao giờ 2 rank nhỏ gặp nhau ngay từ đầu.

#### Trace tay từng bước, dễ nhìn nhất là ví dụ 8 người

```
size = 1:  [1]
              ↓ nhân đôi: mỗi s → thêm (2+1-s)
size = 2:  [1, 2]                              (1↔2, tổng = 3)
              ↓ nhân đôi: mỗi s → thêm (4+1-s)
size = 4:  [1, 4, 2, 3]                        (1↔4 và 2↔3, tổng đều = 5)
              ↓ nhân đôi: mỗi s → thêm (8+1-s)
size = 8:  [1, 8, 4, 5, 2, 7, 3, 6]             (1↔8, 4↔5, 2↔7, 3↔6, tổng đều = 9)
```

Ghép 2 số liên tiếp trong dãy `size=8` thành 4 cặp đấu vòng 1:

```
Trận 1:  hạt giống 1  vs  hạt giống 8
Trận 2:  hạt giống 4  vs  hạt giống 5
Trận 3:  hạt giống 2  vs  hạt giống 7
Trận 4:  hạt giống 3  vs  hạt giống 6
```

Vẽ thành sơ đồ bracket thật để thấy rõ hạt giống 1 và 2 được tách xa nhau thế nào:

```
Vòng 1              Vòng 2 (bán kết)        Vòng 3 (chung kết)
─────────────────────────────────────────────────────────────
Trận 1: 1 vs 8   ─┐
                   ├─ Thắng Trận1 vs Thắng Trận2  ─┐
Trận 2: 4 vs 5   ─┘                                 │
                                                     ├─ CHUNG KẾT
Trận 3: 2 vs 7   ─┐                                 │
                   ├─ Thắng Trận3 vs Thắng Trận4  ─┘
Trận 4: 3 vs 6   ─┘
```

Nhìn sơ đồ là thấy ngay: **hạt giống 1 nằm ở nhánh trên cùng, hạt giống 2 nằm ở nhánh dưới cùng** — 2 nhánh này chỉ gặp nhau ở đúng ô Chung kết. Còn hạt giống 1 và hạt giống 4 tuy cùng "nửa trên" nhưng khác cặp bán kết con (1 ở cặp Trận1+Trận2, còn 4 cũng ở đúng cặp đó — vì 4 là 1 trong 4 hạt giống mạnh nhất, nó được xếp cùng nửa với 1 nhưng khác trận vòng 1) nên chỉ gặp nhau sớm nhất ở bán kết, không sớm hơn.

**Vì sao chắc chắn đúng — giải thích không cần công thức toán:** hãy hình dung việc dựng bracket 8 người bắt đầu từ bracket 4 người `[1,4,2,3]` đã đúng chuẩn (1 và 2 đã ở 2 nửa khác nhau). Khi nhân đôi lên 8 người, mỗi người trong 4 người đó *giữ nguyên vị trí tương đối*, chỉ được ghép thêm 1 "người yếu hơn" đi kèm ngay cạnh. Vì 1 và 2 đã cách xa nhau đúng chuẩn ở cấp 4 người, thì ở cấp 8 người (chỉ thêm người đi kèm, không xáo lại vị trí 1 và 2) chúng vẫn cách xa nhau đúng như vậy — chỉ là khoảng cách "vật lý" giữa chúng nhân đôi lên theo số vòng. Cứ suy luận lùi mãi về `size=2` (trường hợp cơ bản `[1,2]` hiển nhiên đúng), toàn bộ chuỗi nhân đôi đều giữ đúng tính chất này.

### 2.4 Khi số người thật ít hơn số ô trong bracket — luật BYE

Bracket luôn phải có số ô là **lũy thừa của 2** (4, 8, 16, 32...). Nếu giải có 10 người thật, hệ thống chọn bracket 16 ô gần nhất (`16 = 2⁴ ≥ 10`), 6 ô còn thừa được coi là "ô ảo".

Người nào bị ghép với 1 "ô ảo" thì **tự động thắng vòng đó mà không cần đấu** (gọi là BYE — miễn đấu). Và luật ưu tiên là: **6 ô ảo này luôn rơi vào 6 hạt giống mạnh nhất trước** (hạt giống 1–6 được nghỉ vòng 1, hạt giống 7–10 phải đấu thật) — giống hệt cách các giải Grand Slam tennis vẫn làm: hạt giống càng cao càng dễ được ưu tiên "bye" khi bảng đấu không tròn.

---

## 3. SINGLE_ELIMINATION — Loại trực tiếp

Đây là thể thức đơn giản nhất: **thua 1 trận là bị loại ngay**, không có đường lùi.

### 3.1 Cấu trúc

Chỉ có **1 bracket duy nhất**. Số vòng đấu = số lần phải nhân đôi để ra `bracketSize` (VD 16 người → 4 vòng: 1/8, tứ kết, bán kết, chung kết). Vòng 1 xếp theo thuật toán ở mục 2. Từ vòng 2 trở đi, **người thắng tự động lên vòng trên** theo đúng cấu trúc cây đã được "khoá cứng" từ đầu — không xếp lại hạt giống giữa chừng.

### 3.2 Trận tranh hạng 3

Nếu Owner bật tuỳ chọn "Trận tranh hạng 3", hệ thống tạo thêm 1 trận riêng giữa 2 người **thua ở bán kết**, diễn ra song song với chung kết, để phân định rõ ai hạng 3 ai hạng 4 (thay vì để 2 người bán kết thua cùng đứng chung hạng 3-4).

### 3.3 Số ván thắng cần thiết (race-to) tăng dần theo vòng

Mỗi vòng có 1 mức "đấu tới mấy ván thắng" riêng — càng vào sâu, mức này càng cao (VD vòng 1 chỉ cần thắng 5 ván, chung kết phải thắng 9 ván) để những trận quan trọng đòi hỏi phong độ ổn định hơn, không thắng may rủi.

### 3.4 Xếp hạng khi giải kết thúc

| Hạng | Cách xác định |
|---|---|
| #1 (Vô địch), #2 (Á quân) | Người thắng / thua trận chung kết |
| #3, #4 | Người thắng / thua trận tranh hạng 3 — nếu giải không tổ chức trận này thì 2 người thua bán kết **đồng hạng 3-4** |
| #5-8, #9-16... | Người thua ở vòng tương ứng, những người thua **cùng 1 vòng** thì đồng hạng với nhau (nhóm càng thua sớm thì nhóm càng đông — VD thua vòng 1/8 thì 8 người đồng hạng #9-16) |

Người bị loại do "gặp BYE" (không thật sự thua trận nào) thì không tính vào bảng xếp hạng theo kiểu "thua trận" — vì họ không hề thua.

---

## 4. DOUBLE_ELIMINATION — Loại kép (CUT_TO_SE)

> **Lưu ý quan trọng**: hệ thống **không** áp dụng kiểu "đấu loại kép tới tận cùng" (tức 2 nhánh Thắng/Thua đấu độc lập cho tới khi mỗi nhánh chỉ còn 1 người, rồi 2 người đó đấu 1-2 trận chung kết lớn) — vì ngoài đời thực các giải bi-a không tổ chức kiểu này (kéo quá dài). Hệ thống dùng biến thể **CUT_TO_SE** ("cắt về loại trực tiếp"), thực tế hơn nhiều.

### 4.1 Ý tưởng bằng lời — khác gì so với loại trực tiếp thường?

Ở loại trực tiếp thường, thua 1 trận là hết cơ hội — kể cả người mạnh nhất giải nếu chẳng may có 1 ngày thi đấu tệ cũng bị loại oan. Loại kép cho thêm 1 "mạng sống": **thua trận đầu tiên chưa bị loại ngay**, mà rớt xuống 1 nhánh phụ gọi là **Nhánh Thua**, vẫn còn cơ hội đi tiếp nếu thắng liên tục ở đó. Chỉ khi **thua lần thứ 2** mới thực sự bị loại khỏi giải.

Nhưng nếu để 2 nhánh (Thắng và Thua) đấu độc lập tới tận cùng thì giải kéo quá dài (Nhánh Thua phải đấu gần gấp đôi số trận Nhánh Thắng). Giải pháp CUT_TO_SE: cho 2 nhánh đấu song song tới một mốc nhất định (còn lại 1 số người vừa phải, VD 8 người), rồi **gộp** 8 người đó lại đấu **loại trực tiếp bình thường** để ra vô địch — vừa giữ được ý nghĩa "có mạng sống thứ 2", vừa không kéo dài giải quá mức.

```
16 người bắt đầu
   │
   ├── Nhánh Thắng: ai thua 1 trận → rớt xuống Nhánh Thua (chưa bị loại)
   ├── Nhánh Thua:  ai thua ở đây → bị loại thật sự (đã thua 2 lần)
   │
   └── Khi 2 nhánh còn tổng cộng 8 người sống sót
          → GỘP 8 người này lại, đấu loại trực tiếp bình thường (Last-8)
          → Vô địch Last-8 = Vô địch cả giải
```

### 4.2 Công thức tính "đấu bao nhiêu vòng thì gộp"

Owner tự chọn số người muốn gộp về loại trực tiếp (gọi là `se_phase_size`, VD chọn 8). Hệ thống tính ngược lại xem Nhánh Thắng và Nhánh Thua phải đấu bao nhiêu vòng trước khi tới mốc đó:

```
bracketSize   = lũy thừa 2 gần nhất ≥ số người thật             (VD 16 người → bracketSize = 16)
seSize        = lũy thừa 2 gần nhất của se_phase_size            (VD chọn 8   → seSize = 8)
cutoffRound   = log2(bracketSize / seSize) + 1                   → log2(16/8)+1 = 2
lCutoffRounds = 2 × (cutoffRound − 1)                             → 2 × (2−1) = 2
```

Với ví dụ 16 người, `se_phase_size = 8`: **Nhánh Thắng đấu 2 vòng**, **Nhánh Thua đấu 2 vòng**, mỗi nhánh còn lại đúng 4 người sống sót → gộp thành **Last-8**.

*(Nếu Owner chọn số người gộp lớn hơn nửa số người thực tế của giải — VD giải chỉ có 16 người mà đòi gộp 16 người luôn ngay từ đầu — hệ thống tự động "kẹp" con số đó về mức hợp lý nhất, không cho lỗi khó hiểu, chỉ âm thầm điều chỉnh.)*

### 4.3 Ba "khoang" (stage) của 1 giải Loại kép

| Khoang | Vai trò |
|---|---|
| **Nhánh Thắng** | Nơi mọi người bắt đầu. Ai còn nguyên "mạng" thì ở đây. Vòng 1 xếp hạt giống y hệt mục 2 |
| **Nhánh Thua** | Nơi tiếp nhận người vừa thua 1 trận ở Nhánh Thắng. Thua ở đây là bị loại thật |
| **Last-X** | Bracket loại trực tiếp trống, **chỉ điền người vào sau khi** 2 khoang trên đấu xong tới mốc cắt |

### 4.4 Người thua Nhánh Thắng "rớt" xuống Nhánh Thua như thế nào — ai gặp ai được xác định dựa vào đâu?

**Điểm quan trọng cần nhấn mạnh**: việc ghép cặp ở Nhánh Thua **không hề bốc thăm lại, không random, không xếp theo trình độ** — toàn bộ được tính sẵn từ lúc sinh bracket, dựa thuần túy vào **vị trí (positionNo)** của trận vừa thua trong cây bracket, theo đúng 2 quy tắc cố định:

**Quy tắc 1 — người thua vòng 1 Nhánh Thắng: ghép 2 trận liền kề nhau**

```
Người thua W-R1-Trận1  ┐
                         ├──→ ghép chung vào L-R1-Trận1
Người thua W-R1-Trận2  ┘

Người thua W-R1-Trận3  ┐
                         ├──→ ghép chung vào L-R1-Trận2
Người thua W-R1-Trận4  ┘
```

Vì sao ghép 2 trận liền kề chứ không ghép tuỳ ý? Vì người thua Trận1 và người thua Trận2 **chưa từng đấu với nhau** (mỗi người đấu với 1 đối thủ khác) — ghép họ lại đảm bảo không ai vừa thua xong phải đấu lại ngay với chính người vừa hạ mình.

**Quy tắc 2 — người thua vòng ≥2 Nhánh Thắng: rớt đúng vào vị trí tương ứng**

```
Người thua tại W-Vòng(wr), Trận thứ pos  ──→  rớt vào L-Vòng(2×(wr-1)), Trận thứ pos (giữ nguyên số trận)
```

**Bên trong Nhánh Thua, người thắng tự thăng tiến theo 2 kiểu xen kẽ** (không liên quan gì đến chuyện thua ở đâu):
- *Vòng lẻ → vòng chẵn*: giữ nguyên số trận, đứng vào ô "player1", để trống ô "player2" chờ người mới rớt từ Nhánh Thắng xuống
- *Vòng chẵn → vòng lẻ*: 2 trận cạnh nhau gộp làm 1 (thu hẹp dần, không có ai rớt thêm ở bước này)

#### Ví dụ trace tay đầy đủ — Nhánh Thắng/Thua 8 người

```
Nhánh Thắng                              Nhánh Thua
──────────────────────────────────────────────────────────────
W-R1 (4 trận, 8 người vào)
  Trận1: A vs B → B thua      ┐
  Trận2: C vs D → D thua      ┴──→ L-R1-Trận1: B vs D
  Trận3: E vs F → F thua      ┐
  Trận4: G vs H → H thua      ┴──→ L-R1-Trận2: F vs H

W-R2 (2 trận, 4 người thắng W-R1 vào)
  Trận1: A vs C → C thua ─────────→ L-R2-Trận1: (thắng L-R1-Trận1) vs C
  Trận2: E vs G → G thua ─────────→ L-R2-Trận2: (thắng L-R1-Trận2) vs G

                                    L-R2 đấu xong → 2 người thắng GỘP LẠI:
                                    L-R3-Trận1: thắng(L-R2-Trận1) vs thắng(L-R2-Trận2)

W-R3 = Chung kết Nhánh Thắng
  A vs E → giả sử E thua ─────────→ L-R4-Trận1: thắng(L-R3-Trận1) vs E
```

Người thua chung kết Nhánh Thắng (E) rớt xuống đúng trận cuối cùng của Nhánh Thua, gặp đúng người đã sống sót qua hết Nhánh Thua — đây là "chung kết phụ" quyết định ai là người thứ 2 sống sót toàn giải (nếu chạy CUT_TO_SE thì Nhánh Thua dừng sớm hơn ở mốc cắt, nhưng cơ chế ghép cặp áp dụng y hệt cho tới lúc dừng).

*(Nếu người thắng ở Nhánh Thắng nhờ đối thủ vắng mặt — BYE — chứ không phải đấu thật, thì không có ai "thua thật" để rớt xuống, trận Nhánh Thua tương ứng để trống chờ người khác tới sau.)*

### 4.5 Gộp về Last-X — ghép cặp thế nào để công bằng?

Việc gộp chỉ thực hiện được khi **cả 2 nhánh đã đấu xong tới đúng mốc cắt**. Lúc đó có N/2 người sống sót Nhánh Thắng và N/2 người sống sót Nhánh Thua (VD mỗi bên 4 người, tổng 8 vào Last-8). Cách ghép cặp trận đầu tiên của Last-X:

```
Người mạnh nhất còn sống của Nhánh Thắng  ↔  Người yếu nhất còn sống của Nhánh Thua
Người mạnh nhì  của Nhánh Thắng           ↔  Người mạnh nhì (tính ngược) của Nhánh Thua
...
```

Nói cách khác: **luôn ghép 1 người Nhánh Thắng với 1 người Nhánh Thua**, không bao giờ ghép 2 người cùng Nhánh Thắng với nhau ở trận đầu Last-X. Nhờ vậy, những người "còn nguyên mạng" (chưa thua trận nào) chắc chắn không gặp nhau ngay từ đầu Last-X — họ vẫn phải đấu tiếp several vòng nữa mới có thể chạm trán.

### 4.6 Xếp hạng khi giải kết thúc

Đây là phần **dễ làm sai nhất** nếu không cẩn thận — vì 3 khoang (Nhánh Thắng / Nhánh Thua / Last-X) đánh số vòng đấu (1, 2, 3...) **độc lập với nhau**, không thể đem gộp chung rồi so sánh số vòng như thể thức Loại trực tiếp thường được (sẽ nhầm lẫn "người thua vòng 2 Nhánh Thắng" với "người thua bán kết Last-X thật sự" — hai việc hoàn toàn khác về ý nghĩa nhưng nếu gộp thô sẽ bị coi là "cùng vòng 2" một cách sai lầm).

Cách tính đúng chia làm 2 phần:

1. **Hạng 1 đến hạng seSize** (VD hạng 1–8): áp dụng đúng công thức xếp hạng Loại trực tiếp ở mục 3.4, nhưng **chỉ tính trên các trận của khoang Last-X**, bỏ qua hoàn toàn Nhánh Thắng/Thua.
2. **Các hạng còn lại**: những người đã bị loại thật sự (thua ở Nhánh Thua, tức đã thua đủ 2 trận) — ai **thua muộn hơn** (tức trụ lại Nhánh Thua lâu hơn) thì xếp hạng **cao hơn**. Nhánh Thắng hoàn toàn không dùng để tính hạng, vì thua ở đó chỉ là "rớt xuống", chưa phải bị loại.

```
Ví dụ 16 người, gộp về Last-8 (27 trận tổng: 12 Nhánh Thắng + 8 Nhánh Thua + 7 Last-8):

  #1, #2   ← Vô địch / Á quân của Last-8
  #3-4     ← 2 người thua bán kết Last-8 (đúng 2 người, không lẫn ai khác)
  #5-8     ← 4 người thua tứ kết Last-8 (đúng 4 người)
  #9-12    ← 4 người bị loại (thua Nhánh Thua) ở VÒNG CUỐI của Nhánh Thua — tức "suýt" lọt vào Last-8
  #13-16   ← 4 người bị loại sớm hơn ở Nhánh Thua
```

---

## 5. PROGRESSIVE_ROUND_ROBIN — Vòng tròn loại dần

### 5.1 Ý tưởng bằng lời

Thể thức này **không loại ai chỉ vì thua đúng 1 trận** — thay vào đó, mọi người trong 1 giai đoạn đấu **vòng tròn** (ai cũng gặp ai đúng 1 lần), sau đó nhìn vào **cả quá trình** (bao nhiêu trận thắng, hiệu số ván...) để quyết định ai đi tiếp, ai dừng lại. Cứ lặp lại nhiều giai đoạn, mỗi giai đoạn thu hẹp số người, tới giai đoạn cuối thì chuyển sang đấu loại trực tiếp (Playoff) để tìm ra nhà vô địch.

```
Giai đoạn 1: TẤT CẢ N người đấu vòng tròn với nhau
             → xếp hạng theo phong độ cả giai đoạn → giữ lại K1 người đầu bảng
Giai đoạn 2: K1 người đấu vòng tròn tiếp
             → giữ lại K2 người đầu bảng
   ...
Playoff:     K_cuối người đấu loại trực tiếp → ra Vô địch
```

Owner tự cấu hình dãy số `K1, K2, ...` (VD `"10, 6, 4"` nghĩa là: sau giai đoạn 1 giữ 10 người, sau giai đoạn 2 giữ 6 người, 4 người cuối vào Playoff).

### 5.2 Lịch thi đấu vòng tròn — "phương pháp vòng tròn" (circle method)

Bài toán: có N người, cần xếp lịch sao cho **ai cũng gặp ai đúng 1 lần**, và số trận diễn ra mỗi vòng phải đều nhau (để không ai phải đấu 2 trận cùng lúc). Cách làm cổ điển ("circle method" — phương pháp vòng tròn), dễ hình dung như sau:

```
Xếp N người thành 1 vòng tròn, giữ CỐ ĐỊNH người số 0, còn lại xoay vòng.
Mỗi "lượt xoay" = 1 vòng đấu: ghép người đối xứng nhau qua tâm vòng tròn thành 1 cặp.
Xoay đủ N-1 lần thì tất cả các cặp đã gặp nhau hết đúng 1 lần.
```

**Ví dụ 6 người** (đánh số 0–5), người 0 đứng yên, 1-2-3-4-5 xoay quanh:

```
Vòng 1:  (0-5) (1-4) (2-3)
Vòng 2:  (0-4) (5-3) (1-2)
Vòng 3:  (0-3) (4-2) (5-1)
Vòng 4:  (0-2) (3-1) (4-5)
Vòng 5:  (0-1) (2-5) (3-4)
```

→ Sau 5 vòng, mỗi người đã gặp đủ 5 người còn lại, không thiếu không thừa trận nào.

**Nếu số người lẻ**: thêm 1 "người ảo" (dummy) cho đủ chẵn — ai bốc trúng gặp người ảo ở vòng nào thì coi như **nghỉ vòng đó** (không mất điểm, không cộng điểm).

Đây là thuật toán **tất định**: cùng 1 số lượng người luôn cho ra đúng cùng 1 cấu trúc lịch (vòng nào, trận thứ mấy) — chỉ có **ai đứng ở vị trí nào** trong vòng tròn là phụ thuộc vào thứ tự rank đầu vào (mục 2.2). Nhưng khác với thể thức loại trực tiếp, ở đây **thứ tự ban đầu không quyết định ai mạnh gặp ai yếu trước** — vì ai cũng phải gặp hết tất cả mọi người, thứ tự chỉ ảnh hưởng đến việc "gặp ai ở vòng thứ mấy" mà thôi.

### 5.3 Sau mỗi giai đoạn, ai đi tiếp? — tiêu chí xếp hạng giai đoạn

Đấu vòng tròn xong 1 giai đoạn, cần xếp hạng để chọn ra người đi tiếp. Thứ tự ưu tiên so sánh (nếu tiêu chí trước bằng nhau mới xét tiêu chí sau):

1. **Số trận thắng** — thắng nhiều xếp trên
2. **Hiệu số ván** (tổng ván thắng trừ tổng ván thua trong cả giai đoạn) — ai thắng "đậm" hơn xếp trên
3. **Đối đầu trực tiếp** — nếu 2 người vẫn ngang nhau ở 2 tiêu chí trên, ai thắng người kia trong trận trực tiếp thì xếp trên
4. **Tổng số ván thắng** (không trừ ván thua) — ai ghi nhiều ván thắng hơn xếp trên
5. **Mã số người tham gia** — tiêu chí "chốt hạ" cuối cùng, đảm bảo luôn phân định được thứ hạng rõ ràng, không bao giờ có 2 người "đồng hạng mập mờ" không rõ ai hơn ai

Người lọt vào top `K` theo cấu hình thì đi tiếp giai đoạn sau; người còn lại coi như dừng cuộc chơi tại đây (đã bị loại khỏi giải, nhưng vẫn được tính hạng dựa vào lúc bị dừng — xem mục 5.5).

### 5.4 Vào tới Playoff — xếp hạt giống lại theo phong độ thật

Nhóm cuối cùng lọt vào Playoff **không** dùng thứ tự hạt giống ban đầu lúc vào giải nữa, mà dùng **đúng thứ hạng vừa đấu được ở vòng tròn cuối** — người đứng đầu bảng (phong độ tốt nhất qua nhiều trận thật) được coi như "hạt giống 1" của Playoff, áp dụng lại chính thuật toán `standardSeedOrder` ở mục 2.3 để tách nhánh. Điều này công bằng hơn nhiều so với giữ nguyên hạt giống ban đầu — vì phong độ thật trong giải mới là thước đo đáng tin nhất.

### 5.5 Xếp hạng chung cuộc toàn giải

```
Nhóm vào Playoff:     xếp theo kết quả Playoff (Vô địch, Á quân, Bán kết...) — dùng công thức mục 3.4
Nhóm bị loại dọc đường: xếp theo GIAI ĐOẠN bị dừng lại — dừng ở giai đoạn CÀNG MUỘN thì hạng CÀNG CAO
                        (vì trụ được càng lâu chứng tỏ càng mạnh)
                        Trong cùng 1 giai đoạn bị dừng thì xếp tiếp theo đúng bảng xếp hạng của giai đoạn đó
```

---

## 6. GROUP_PLAYOFF — ghi chú

> ⚠️ **Đã lỗi thời — xem bản đính chính ở [mục 7.5](#7-thuật-toán-sinh-trận-đấu-bracket-generation--cách-tạo-cây-match-trong-db).** Mô tả gốc bên dưới (giữ lại để đối chiếu lịch sử) nói `GROUP_PLAYOFF` "rơi vào nhánh mặc định, chạy y hệt SINGLE_ELIMINATION". Đó **không còn đúng**: đối chiếu code hiện tại (`TournamentFormat.java`, `enums/`) thì `GROUP_PLAYOFF` đã bị **gỡ hoàn toàn khỏi enum**, không còn là lựa chọn để Owner chọn nữa — không phải "chạy sai thầm lặng" như mô tả cũ, mà bốc thăm với format lạ giờ **báo lỗi rõ ràng**. Chi tiết quá trình gỡ bỏ và lý do xem `bracket-seeding-issues.md` (cùng thư mục).

Mô tả gốc (lịch sử, tại thời điểm `GROUP_PLAYOFF` còn tồn tại trong enum): thể thức này (`"Vòng bảng + Playoff"`) vẫn còn hiện trong danh sách cho Owner chọn khi tạo giải, nhưng thực tế **không có đoạn code xử lý riêng** — hệ thống rơi vào nhánh xử lý mặc định và chạy y hệt **SINGLE_ELIMINATION** (loại trực tiếp thẳng), **không hề chia bảng vòng tròn như tên gọi**. Nếu ai chọn thể thức này sẽ bị hiểu nhầm là "chia bảng" nhưng thực chất ra kết quả loại trực tiếp bình thường.

---

## 7. Thuật toán sinh trận đấu (bracket generation) — cách tạo cây `Match` trong DB

Mục 2 ở trên trả lời câu hỏi "**cho sẵn N ô trống trong bracket, ai đứng vào ô nào**". Mục này trả lời câu hỏi khác hẳn: "**cái bracket đó — bao nhiêu trận, bao nhiêu vòng, ai thắng trận nào thì đi tiếp vào đúng trận nào — được dựng lên bằng thuật toán gì?**" Đây là bước chạy **trước** bước xếp hạt giống của mục 2: hệ thống dựng khung cây trận đấu rỗng (chưa có người nào cả) trước, sau đó mới điền người vào các ô vòng 1 theo thuật toán mục 2.

### 7.1 Khuôn dùng chung cho mọi bracket loại trực tiếp: lưới `grid[round][position]`

4 loại bracket khác nhau trong hệ thống — SE, nhánh Thắng của DE, nhánh Thua của DE, bracket Last-X của DE, và Playoff của PRR — đều được dựng bằng **đúng 1 khuôn thuật toán**, chỉ khác số vòng bắt đầu/kết thúc và tiền tố `matchCode`. Khuôn đó gồm 2 bước.

**Bước A — cấp phát toàn bộ trận rỗng theo lưới 2 chiều** ([BracketGenerationServiceImpl.java:157-179](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L157-L179)):

```java
int bracketSize = nextPowerOf2(n);      // ép về luỹ thừa 2 gần nhất ≥ n
int totalRounds = log2(bracketSize);    // VD bracketSize=8 → 3 vòng

Match[][] grid = new Match[totalRounds + 1][(bracketSize / 2) + 1];  // 1-indexed

for (int round = 1; round <= totalRounds; round++) {
    int mc = bracketSize >> round;              // số trận vòng này = bracketSize / 2^round
    for (int pos = 1; pos <= mc; pos++) {
        grid[round][pos] = matchRepository.save(Match.builder()
                .roundNo(round).positionNo(pos)
                .matchCode("R%d-M%d".formatted(round, pos))
                .status(PENDING).player1Score(0).player2Score(0).build());
    }
}
```

VD `bracketSize=8`: vòng 1 có 4 trận, vòng 2 có 2 trận, vòng 3 (chung kết) có 1 trận — tổng `8-1=7` trận, đúng số trận tối thiểu để loại 7 người, còn lại 1 người vô địch.

**Bước B — nối trận vòng này với đúng 1 trận vòng sau, bằng 1 công thức số học duy nhất** ([:182-191](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L182-L191)):

```java
for (int round = 1; round < totalRounds; round++) {
    int mc = bracketSize >> round;
    for (int pos = 1; pos <= mc; pos++) {
        int pp     = (pos + 1) / 2;                       // trận cha ở vòng sau
        String slot = (pos % 2 == 1) ? "player1" : "player2";
        grid[round][pos].setNextMatchWin(grid[round + 1][pp]);
        grid[round][pos].setWinSlot(slot);
    }
}
```

**Vì sao công thức `pp = (pos+1)/2` luôn đúng?** Vì 2 trận liền kề ở vòng hiện tại — trận số lẻ `2k-1` và trận số chẵn `2k` — luôn phải "đổ" vào đúng 1 trận số `k` ở vòng sau (2 người thắng gặp nhau). Thay `pos=2k-1`: `(2k-1+1)/2 = k`. Thay `pos=2k`: `(2k+1)/2 = k` (chia nguyên trong Java bỏ phần dư). Cả 2 cùng ra `k` — đúng như mong đợi. Trận lẻ luôn đứng vào `player1` của trận cha, trận chẵn đứng vào `player2` — quy ước cố định để không bao giờ 2 người thắng bị ghi đè vào cùng 1 ô.

Sau bước A+B, bracket 8 người đã có đủ 7 trận và toàn bộ dây nối thắng-thì-đi-đâu — nhưng **chưa có người nào cả**. Mục 2 (`assignSeededRound1`) mới là bước điền người vào 4 trận vòng 1.

### 7.2 SINGLE_ELIMINATION

`generateSingleElimination()` ([:155-215](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L155-L215)) áp dụng nguyên khuôn 7.1, `matchCode = "R%d-M%d"`. Chỉ thêm 1 phần mà khuôn 7.1 không có: **trận tranh hạng 3**, nối bằng 1 cặp thuộc tính khác hẳn — `nextMatchLose` / `loseSlot` (thay vì `nextMatchWin` / `winSlot`):

```java
Match thirdPlace = matchRepository.save(Match.builder()
        .roundNo(totalRounds).positionNo(2).matchCode("3RD")...build());

grid[sfRound][1].setNextMatchLose(thirdPlace); grid[sfRound][1].setLoseSlot("player1");
grid[sfRound][2].setNextMatchLose(thirdPlace); grid[sfRound][2].setLoseSlot("player2");
```

`sfRound = totalRounds - 1` chính là vòng bán kết (luôn có đúng 2 trận). Người **thua** ở 2 trận bán kết bị định tuyến sang trận `3RD` thay vì bị loại hẳn khỏi cây — lần duy nhất trong SE mà "người thua" vẫn được đưa tiếp vào 1 trận khác.

### 7.3 DOUBLE_ELIMINATION (CUT_TO_SE) — dựng 3 bracket riêng biệt cùng lúc

`generateCutToSEDE()` ([:1214-1367](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L1214-L1367)) là phần phức tạp nhất: dựng **3 bracket độc lập** trong cùng 1 lần bốc thăm — nhánh Thắng (W), nhánh Thua (L), và bracket Last-X (SE) — rồi nối chúng lại bằng các "cầu" (drop) giữa nhánh Thắng và nhánh Thua.

**7.3.1 Tính trước 2 con số quyết định kích thước 3 bracket:**

```java
int cutoffRound   = log2(bracketSize / seSize) + 1;   // số vòng nhánh Thắng đấu trước khi cắt
int lCutoffRounds = 2 * (cutoffRound - 1);              // số vòng nhánh Thua tương ứng
```

VD 16 người, `seSize=8` → `cutoffRound = log2(16/8)+1 = 2`, `lCutoffRounds = 2×(2-1) = 2` — khớp đúng ví dụ tay ở mục 4.2.

**7.3.2 Nhánh Thắng (W)** — dựng bằng đúng khuôn 7.1, chỉ dừng ở `cutoffRound` thay vì đi hết `totalRounds`; `matchCode = "W-R%d-M%d"`.

**7.3.3 Nhánh Thua (L)** — số trận mỗi vòng **không** theo công thức `bracketSize >> round` như W, mà theo công thức riêng vì mỗi vòng L còn nhận thêm người vừa rớt từ W ([:841-843](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L841-L843)):

```java
private int losersMatchCount(int bracketSize, int lr) {
    return Math.max(1, bracketSize >> (((lr + 1) / 2) + 1));
}
```

Việc nối trận **trong nội bộ nhánh L** cũng khác W — không dùng chung 1 công thức `pp=(pos+1)/2` cho mọi vòng, mà **xen kẽ 2 kiểu** theo vòng lẻ/chẵn:

```java
if (lr % 2 == 1) {
    // Vòng lẻ → vòng chẵn: GIỮ NGUYÊN vị trí, đứng vào player1 (player2 để trống chờ người rớt từ W)
    lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pos]); lGrid[lr][pos].setWinSlot("player1");
} else {
    // Vòng chẵn → vòng lẻ: NÉN 2 trận cạnh nhau thành 1 (không ai rớt thêm ở bước này)
    int pp = (pos + 1) / 2;
    lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pp]);
    lGrid[lr][pos].setWinSlot((pos % 2 == 1) ? "player1" : "player2");
}
```

Lý do phải xen kẽ 2 kiểu: nhánh L vừa phải "hút" người mới rớt từ W (không được nén quân số vòng đó) vừa phải tự thu hẹp dần (nén quân số). Hai việc này không thể làm cùng lúc trong 1 vòng, nên hệ thống tách thành 2 vòng liên tiếp làm 2 việc khác nhau, xen kẽ tới hết `lCutoffRounds`.

**7.3.4 "Cầu" nối W → L (`wireWtoL`)** — phần khiến DE khác hẳn 1 bracket đơn: mỗi trận W (trừ trận BYE) phải trỏ `nextMatchLose` sang đúng 1 ô của L. Có 2 công thức khác nhau tuỳ vòng ([:1343-1359](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L1343-L1359)):

```java
// W-R1: 2 trận liền kề (2k-1, 2k) cùng đổ vào 1 trận L-R1 thứ k — vì 2 người thua W-R1-Trận(2k-1)
// và W-R1-Trận(2k) CHƯA từng đấu nhau, ghép họ lại là hợp lệ (không ai vừa thắng vừa gặp lại đối thủ vừa hạ)
wireWtoL(wGrid, 1, 2*lPos-1, lGrid, 1, lPos, "player1");
wireWtoL(wGrid, 1, 2*lPos,   lGrid, 1, lPos, "player2");

// W-R(wr≥2): mỗi trận thua đổ ĐÚNG 1-1 vào ô player2 của L-R(2*(wr-1)) cùng số thứ tự pos
wireWtoL(wGrid, wr, pos, lGrid, 2*(wr-1), pos, "player2");
```

`wireWtoL()` tự bỏ qua nếu trận W đó là BYE (`isBye=true`) — vì không ai "thua thật" để rớt xuống, ô L tương ứng để trống chờ người khác tới sau.

**7.3.5 Bracket Last-X (SE)** — dựng rỗng hoàn toàn bằng khuôn 7.1 (`matchCode="SE-R%d-M%d"`, `totalRounds=log2(seSize)`) **ngay tại thời điểm bốc thăm**, dù chưa biết ai sẽ vào đó — phải chờ W và L đấu xong tới vòng cắt mới có người để điền.

**7.3.6 Gộp về Last-X — `populateFinalBracket()`, gọi riêng sau khi W+L đấu xong:**

Đây là 1 bước **tách biệt hẳn** khỏi lúc bốc thăm (Owner bấm 1 nút khác, sau khi 2 nhánh đã đấu hết vòng cắt). Ghép theo kiểu "snake" — mạnh nhất còn sống của W gặp yếu nhất còn sống của L ([:1432-1443](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L1432-L1443)):

```java
for (int k = 0; k < half; k++) {                          // half = seSize/2
    seR1.get(k).setPlayer1(wSurvivors.get(k));               // W thứ k+1 mạnh nhất còn lại
    seR1.get(k).setPlayer2(lSurvivors.get(half - 1 - k));     // L đối xứng tính từ cuối lên
}
```

khớp đúng nguyên tắc đã nêu ở mục 4.5 — không có 2 người cùng nhánh W gặp nhau ở trận đầu Last-X.

### 7.4 PROGRESSIVE_ROUND_ROBIN — sinh lịch vòng tròn thật bằng "circle method", và Playoff rỗng dựng sẵn từ đầu

Khác hẳn SE/DE (chỉ có 1 kiểu cây "loại trực tiếp"), PRR cần 1 thuật toán hoàn toàn khác để sinh **lịch vòng tròn** (ai gặp ai, ở vòng nào) — đây là bản cài đặt thật của ví dụ tay ở mục 5.2 ([:318-338](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L318-L338)):

```java
private List<int[]> roundRobinSchedule(int count) {
    int sz = count % 2 == 0 ? count : count + 1;   // lẻ → thêm 1 "người ảo" (dummy) cho chẵn
    int[] ring = new int[sz];
    for (int i = 0; i < sz; i++) ring[i] = i;
    int totalRounds = sz - 1;
    for (int round = 1; round <= totalRounds; round++) {
        int posNo = 0;
        for (int k = 0; k < sz / 2; k++) {
            int a = ring[k], b = ring[sz - 1 - k];      // ghép đối xứng qua tâm
            if (a == count || b == count) continue;      // gặp dummy → bỏ, người đó nghỉ vòng này
            posNo++;
            schedule.add(new int[]{round, posNo, a, b});
        }
        int last = ring[sz - 1];                          // "xoay vòng": người cuối nhảy lên vị trí 1,
        System.arraycopy(ring, 1, ring, 2, sz - 2);        // còn lại dồn xuống — chỉ người số 0 đứng yên
        ring[1] = last;
    }
    return schedule;
}
```

Vòng lặp `System.arraycopy` + `ring[1]=last` chính là "phép xoay" 1 nấc mỗi vòng đã mô tả bằng lời ở mục 5.2. Hàm này **tất định** (deterministic) và **không đụng tới thứ tự rank** — nó chỉ sinh cấu trúc (vòng nào, trận thứ mấy, vị trí `a`/`b` nào gặp nhau), còn ai đứng ở vị trí `a`/`b` là việc của bước điền người riêng bên dưới.

**Điền người dùng lại đúng schedule đó, ở 2 thời điểm khác nhau:**

```java
// Lúc bốc thăm — GĐ1 điền NGAY (orderedPlayers != null), GĐ2..N tạo TRỐNG (null, chỉ có khung)
private List<Match> buildRoundRobin(..., List<Participant> orderedPlayers, ...) {
    for (int[] s : roundRobinSchedule(count)) {
        Participant p1 = orderedPlayers != null ? orderedPlayers.get(s[2]) : null;
        Participant p2 = orderedPlayers != null ? orderedPlayers.get(s[3]) : null;
        matchRepository.save(Match.builder().roundNo(s[0]).positionNo(s[1])
                .player1(p1).player2(p2)...build());
    }
}

// Sau khi advance từ GĐ trước — điền lại đúng những ô TRỐNG đã tạo sẵn, khớp theo (round, positionNo)
private void fillRoundRobinPlayers(TournamentStage stage, int count, List<Participant> orderedPlayers) {
    for (int[] s : roundRobinSchedule(count)) {
        Match m = byKey.get(s[0] + "-" + s[1]);            // TÌM LẠI đúng ô đã tạo lúc bốc thăm
        m.setPlayer1(orderedPlayers.get(s[2]));
        m.setPlayer2(orderedPlayers.get(s[3]));
    }
}
```

**Điểm mấu chốt:** cả `buildRoundRobin` (sinh khung lúc bốc thăm) và `fillRoundRobinPlayers` (điền người sau khi advance) đều gọi lại **cùng một hàm** `roundRobinSchedule(count)` — nên dù được gọi ở 2 thời điểm cách nhau cả tuần (bốc thăm → đấu xong GĐ1 → advance sang GĐ2), cấu trúc `(round, positionNo)` luôn khớp tuyệt đối, không cần lưu thêm bảng ánh xạ nào khác để nhớ "ô này lúc trước dành cho ai".

**Playoff cuối cùng** (`buildEmptyPlayoffBracket()`, [:378-410](src/main/java/com/capstone/su26_sep490_g2_be/service/impl/BracketGenerationServiceImpl.java#L378-L410)) không có gì mới — dựng rỗng bằng **đúng khuôn 7.1** (`matchCode="PO-R%d-M%d"`), y hệt cách SE và W/L/SE-bracket của DE đã làm. Cả bracket Playoff này được tạo **ngay lúc bốc thăm**, cùng lúc với toàn bộ các giai đoạn vòng tròn — Owner nhìn thấy trọn lộ trình GĐ1 → GĐ2 → ... → Chung kết ngay từ đầu, dù người thật của GĐ2 trở đi và của Playoff còn chưa biết là ai.

### 7.5 GROUP_PLAYOFF — không còn tồn tại trong code (đính chính so với mục 1 và 6)

> Tại thời điểm viết bổ sung này (2026-08-14), `GROUP_PLAYOFF` đã bị **gỡ hoàn toàn khỏi enum** `TournamentFormat` — không chỉ "rơi vào nhánh xử lý mặc định" như mục 1/6 mô tả lúc tài liệu gốc được viết (2026-08-13). Đối chiếu code hiện tại:

```java
// TournamentFormat.java — chỉ còn đúng 3 giá trị
SINGLE_ELIMINATION, DOUBLE_ELIMINATION, PROGRESSIVE_ROUND_ROBIN

// generate() — switch tường minh, KHÔNG có nhánh default fallback về SE nữa
BracketResult result = switch (format) {
    case "SINGLE_ELIMINATION"      -> generateSingleElimination(...);
    case "DOUBLE_ELIMINATION"      -> generateDoubleElimination(...);
    case "PROGRESSIVE_ROUND_ROBIN" -> generateProgressiveRoundRobin(...);
    default -> throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED_FOR_DRAW);
};
```

Nếu vì lý do gì đó `tournament.format` mang giá trị lạ (dữ liệu cũ còn sót `GROUP_PLAYOFF`), bốc thăm giờ **báo lỗi rõ ràng** thay vì âm thầm chạy sai như SE — khác với mô tả cũ ở mục 6. Chi tiết quá trình gỡ bỏ xem `bracket-seeding-issues.md` (cùng thư mục).

### 7.6 Tổng số trận sinh ra mỗi thể thức — nhìn nhanh

| Thể thức | Công thức / cách đếm | Ví dụ 16 người |
|---|---|---|
| SINGLE_ELIMINATION | `bracketSize - 1` (+1 nếu bật trận tranh hạng 3) | 15 trận (16 nếu có tranh hạng 3) |
| DOUBLE_ELIMINATION (CUT_TO_SE) | Số trận W (tới `cutoffRound`) + số trận L (tới `lCutoffRounds`, theo `losersMatchCount`) + số trận SE (`seSize-1`) | Với `seSize=8`: 12 (W) + 8 (L) + 7 (SE) = 27 trận — khớp ví dụ mục 4.6 |
| PROGRESSIVE_ROUND_ROBIN | Mỗi giai đoạn: `count×(count-1)/2` trận vòng tròn (bỏ trận gặp dummy nếu số người lẻ) + `playoffSize - 1` trận Playoff | Config `"10,6,4"` từ 16 người: GĐ1 (16 người) 120 + GĐ2 (10 người) 45 + GĐ3 (6 người) 15 + Playoff (4 người) 3 = **183 trận** — vì sao PRR luôn nhiều trận hơn hẳn SE/DE |

---

## 8. Bảng so sánh tổng hợp

| Tiêu chí | SINGLE_ELIMINATION | DOUBLE_ELIMINATION (CUT_TO_SE) | PROGRESSIVE_ROUND_ROBIN |
|---|---|---|---|
| Ý tưởng cốt lõi | Thua 1 trận là hết | Thua 1 trận còn cơ hội gỡ ở Nhánh Thua, thua 2 lần mới hết | Không loại theo từng trận, xét cả quá trình 1 giai đoạn |
| Số "khoang" (stage) | 1 | 3 (Nhánh Thắng / Nhánh Thua / Last-X) | Nhiều giai đoạn vòng tròn + 1 Playoff |
| Cách xếp hạt giống | `standardSeedOrder` ngay vòng 1 | `standardSeedOrder` cho vòng 1 Nhánh Thắng; Last-X ghép Thắng↔Thua đối xứng | Vòng tròn không cần tách nhánh; Playoff mới dùng lại `standardSeedOrder` theo phong độ thật |
| Cấu hình đặc thù | Có/không trận tranh hạng 3 | Chọn số người gộp về Last-X (`se_phase_size`) | Chọn số người giữ lại mỗi giai đoạn |
| Cách xếp hạng cuối | Theo vòng bị loại, gộp nhóm đồng hạng theo luỹ thừa 2 | Tách riêng: hạng 1..seSize theo Last-X, còn lại theo vòng bị loại ở Nhánh Thua | Playoff trước, còn lại theo giai đoạn bị dừng (muộn hơn = hạng cao hơn) |

---

*Tài liệu tạo ngày 2026-08-13, bổ sung mục 7 (thuật toán sinh trận đấu) và đính chính mục 6 ngày 2026-08-14, dựa trên mã nguồn `BracketGenerationServiceImpl.java` và `TournamentResultServiceImpl.java` — repo `SU26_SEP490_G2_BE`, nhánh `prod`.*
