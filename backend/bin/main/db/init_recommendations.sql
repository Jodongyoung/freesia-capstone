-- recommendations 테이블 초기 데이터 삽입 (한글 인코딩 보장)
-- 실행 방법: MySQL Workbench 또는 명령줄에서 실행
-- 먼저 기존 데이터를 삭제: DELETE FROM recommendations;

-- 기쁨 감정 데이터 (4 개 카테고리)
INSERT INTO recommendations (category, emotion, title, description, image_url, content_url) VALUES
('MUSIC', '기쁨', '신나는 KPOP 댄스곡', '기분 좋은 하루를 시작하는 신나는 KPOP 댄스곡입니다. 리듬에 맞춰 춤추며 기분을 업해보세요!', 'https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ'),
('BOOK', '기쁨', '행복한 하루를 위한 독서', '매일 작은 행복을 찾는 방법. 긍정적인 마인드로 하루를 시작해보세요.', 'https://kyobobook.co.kr/images/book.jpg', 'https://www.kyobobook.co.kr/product/detailView.kor?mallGb=KOR&ejkGb=KOR&barcode=9788901234567'),
('MOVIE', '기쁨', '웃음 폭발 코미디 영화', '하루의 스트레스를 날려버릴 코미디 영화. 큰 웃음으로 기분 전환하세요!', 'https://image.kmbox.co.kr/movie.jpg', 'https://www.watcha.com/contents/pm_0123456789'),
('ACTIVITY', '기쁨', '친구와 함께하는 피크닉', '봄날, 친구들과 함께 공원에서 피크닉을 즐기며 즐거운 시간을 보내세요.', 'https://naver.com/blog/activity.jpg', 'https://blog.naver.com/freesia-picnic');

-- 슬픔 감정 데이터 (4 개 카테고리)
INSERT INTO recommendations (category, emotion, title, description, image_url, content_url) VALUES
('MUSIC', '슬픔', '감성적인 KPOP 발라드', '슬픈 마음을 위로해주는 감성 발라드. 눈물을 흘리며 마음을 정리해보세요.', 'https://img.youtube.com/vi/abc123def456/hqdefault.jpg', 'https://www.youtube.com/watch?v=abc123def456'),
('BOOK', '슬픔', '위로가 되는 시집', '슬픈 날 읽으면 마음이 편안해지는 시 모음. 당신의 감정을 이해해줄 시인들.', 'https://kyobobook.co.kr/images/poem.jpg', 'https://www.kyobobook.co.kr/product/detailView.kor?mallGb=KOR&ejkGb=KOR&barcode=9788901987654'),
('MOVIE', '슬픔', '감동적인 드라마 영화', '눈물을 자아내는 감동 드라마. 슬픔을 치유하는 영화 속 위로.', 'https://image.kmbox.co.kr/drama.jpg', 'https://www.watcha.com/contents/pm_0987654321'),
('ACTIVITY', '슬픔', '혼자 하는 명상', '조용한 공간에서 혼자 명상하며 마음을 진정시켜보세요. 깊은 호흡으로 스트레스를 날려요.', 'https://naver.com/blog/meditation.jpg', 'https://blog.naver.com/freesia-meditation');

-- 분노 감정 데이터 (4 개 카테고리)
INSERT INTO recommendations (category, emotion, title, description, image_url, content_url) VALUES
('MUSIC', '분노', '강렬한 록 팝 음악', '분노를 에너지로 전환하는 강렬한 록 팝. 크게 노래부르며 화를 풀어보세요!', 'https://img.youtube.com/vi/rock123music/hqdefault.jpg', 'https://www.youtube.com/watch?v=rock123music'),
('BOOK', '분노', '자신감을 찾는 법', '분노를 올바른 방향으로 사용하는 법. 자신감을 회복하는 자기계발서.', 'https://kyobobook.co.kr/images/confidence.jpg', 'https://www.kyobobook.co.kr/product/detailView.kor?mallGb=KOR&ejkGb=KOR&barcode=9788901111222'),
('MOVIE', '분노', '액션 영화 모음', '분노를 발산할 수 있는 액션 영화. 박진감 넘치는 스토리로 스트레스 해소!', 'https://image.kmbox.co.kr/action.jpg', 'https://www.watcha.com/contents/pm_1112223333'),
('ACTIVITY', '분노', '운동으로 스트레스 해소', '헬스장에서 운동하며 분노를 에너지로 전환하세요. 달리기, 수영 등 유산소 운동 추천!', 'https://naver.com/blog/fitness.jpg', 'https://blog.naver.com/freesia-fitness');

-- 중립 감정 데이터 (4 개 카테고리)
INSERT INTO recommendations (category, emotion, title, description, image_url, content_url) VALUES
('MUSIC', '중립', '차분한 어쿠스틱 팝', '조용한 어쿠스틱 기타 연주가 함께하는 차분한 팝 음악. 평온한 시간을 보내세요.', 'https://img.youtube.com/vi/acoustic456/hqdefault.jpg', 'https://www.youtube.com/watch?v=acoustic456'),
('BOOK', '중립', '일상 속 작은 발견', '매일의 작은 순간들을 발견하는 방법. 일상의 소중함을 일깨워주는 에세이.', 'https://kyobobook.co.kr/images/daily.jpg', 'https://www.kyobobook.co.kr/product/detailView.kor?mallGb=KOR&ejkGb=KOR&barcode=9788901333444'),
('MOVIE', '중립', '휴먼 드라마', '일상 속 사람들의 이야기를 담은 감성 드라마. 공감과 위로를 경험해보세요.', 'https://image.kmbox.co.kr/human.jpg', 'https://www.watcha.com/contents/pm_4445556666'),
('ACTIVITY', '중립', '독서 카페 방문', '조용한 독서 카페에서 책 한 권과 함께 여유로운 시간을 보내세요.', 'https://naver.com/blog/cafe.jpg', 'https://blog.naver.com/freesia-cafe');

-- 추가 데이터 (각 감정당 1 개씩 더)
INSERT INTO recommendations (category, emotion, title, description, image_url, content_url) VALUES
('MUSIC', '기쁨', '청량한 JPOP 팝', '상쾌한 JPOP 팝송으로 하루를 시작해보세요. 청량한 멜로디가 기분을 업시켜줍니다!', 'https://img.youtube.com/vi/jpop789/hqdefault.jpg', 'https://www.youtube.com/watch?v=jpop789'),
('MUSIC', '슬픔', '이별 JPOP 발라드', '이별의 아픔을 위로해주는 JPOP 발라드. 슬픈 마음을 함께 나누어요.', 'https://img.youtube.com/vi/sadballad123/hqdefault.jpg', 'https://www.youtube.com/watch?v=sadballad123'),
('MUSIC', '분노', '비트 빠른 JPOP 댄스', '빠른 비트의 JPOP 댄스곡. 에너지 넘치는 리듬으로 화를 풀어보세요!', 'https://img.youtube.com/vi/fastbeat456/hqdefault.jpg', 'https://www.youtube.com/watch?v=fastbeat456'),
('MUSIC', '중립', '잔잔한 JPOP 발라드', '잔잔한 JPOP 발라드로 마음을 진정시켜보세요. 차분한 멜로디가 평안을 줍니다.', 'https://img.youtube.com/vi/calmjpop789/hqdefault.jpg', 'https://www.youtube.com/watch?v=calmjpop789');