-- recommendations 테이블 한글 인코딩 수정 스크립트
-- 실행 방법: MySQL Workbench 또는 명령줄에서 실행

-- 1. 데이터베이스 인코딩 변경
ALTER DATABASE freesia 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 2. recommendations 테이블 인코딩 변경
ALTER TABLE recommendations 
CONVERT TO CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 3. 각 컬럼의 인코딩 확인
SELECT 
    COLUMN_NAME, 
    CHARACTER_SET_NAME, 
    COLLATION_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'freesia' 
AND TABLE_NAME = 'recommendations';