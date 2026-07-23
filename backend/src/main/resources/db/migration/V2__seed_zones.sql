-- Seed zones for Jantar Mantar site (example layout)
INSERT INTO zones (code, name_en, name_hi, landmark_en, landmark_hi, handoff_point, svg_x, svg_y, sort_order) VALUES
('A', 'Zone A — North Gate', 'ज़ोन ए — उत्तरी द्वार', 'Blue tarp near north barricade', 'उत्तरी बैरिकेड के पास नीली तिरपाल', 'North intake desk', 120, 40, 1),
('B', 'Zone B — Stage Area', 'ज़ोन बी — मंच क्षेत्र', 'Main stage, east side', 'मुख्य मंच, पूर्वी ओर', 'Stage supply tent', 200, 120, 2),
('C', 'Zone C — West Corner', 'ज़ोन सी — पश्चिम कोना', 'West corner under tree', 'पेड़ के नीचे पश्चिम कोना', 'West handoff table', 60, 160, 3),
('D', 'Zone D — South End', 'ज़ोन डी — दक्षिण छोर', 'South barricade, orange flag', 'दक्षिण बैरिकेड, नारंगी झंडा', 'South supply point', 140, 220, 4),
('E', 'Zone E — Central', 'ज़ोन ई — केंद्र', 'Central open area', 'केंद्रीय खुला क्षेत्र', 'Central intake', 160, 140, 5),
('F', 'Zone F — East Side', 'ज़ोन एफ — पूर्वी ओर', 'East walkway', 'पूर्वी रास्ता', 'East tent', 260, 100, 6),
('G', 'Zone G — Medical Lane', 'ज़ोन जी — चिकित्सा लेन', 'Near first aid post', 'प्राथमिक चिकित्सा के पास', 'Medical intake', 100, 100, 7),
('H', 'Zone H — Overnight Camp', 'ज़ोन एच — रात्रि शिविर', 'Overnight camping area', 'रात्रि शिविर क्षेत्र', 'Night watch desk', 180, 180, 8);

INSERT INTO aid_points (zone_id, name, status, hours_note, cannot_handle) VALUES
(7, 'First Aid Point G', 'OPEN', '24 hours', 'Major trauma — call 108'),
(4, 'Aid Station D', 'OPEN', '8am–10pm', 'Cannot handle fractures');
