-- =====================================================================
-- Token usage detail upgrade: input/output cost split, latency, charge mode
-- Existing Docker data volumes do not rerun init scripts; run manually:
--   docker exec -i jap-mysql mysql -uroot -proot --default-character-set=utf8mb4 < infrastructure/mysql/init/10-token-usage-detail.sql
-- =====================================================================

USE `java_agent_platform`;

SET @input_cost_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_token_usage' AND column_name = 'input_cost_micros'
);
SET @ddl = IF(@input_cost_exists = 0,
    'ALTER TABLE `ai_token_usage` ADD COLUMN `input_cost_micros` BIGINT NOT NULL DEFAULT 0 COMMENT ''input token charged micros'' AFTER `total_tokens`',
    'SELECT 1');
PREPARE stmt_input_cost FROM @ddl;
EXECUTE stmt_input_cost;
DEALLOCATE PREPARE stmt_input_cost;

SET @output_cost_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_token_usage' AND column_name = 'output_cost_micros'
);
SET @ddl = IF(@output_cost_exists = 0,
    'ALTER TABLE `ai_token_usage` ADD COLUMN `output_cost_micros` BIGINT NOT NULL DEFAULT 0 COMMENT ''output token charged micros'' AFTER `input_cost_micros`',
    'SELECT 1');
PREPARE stmt_output_cost FROM @ddl;
EXECUTE stmt_output_cost;
DEALLOCATE PREPARE stmt_output_cost;

SET @duration_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_token_usage' AND column_name = 'duration_ms'
);
SET @ddl = IF(@duration_exists = 0,
    'ALTER TABLE `ai_token_usage` ADD COLUMN `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT ''model request duration milliseconds'' AFTER `cost_micros`',
    'SELECT 1');
PREPARE stmt_duration FROM @ddl;
EXECUTE stmt_duration;
DEALLOCATE PREPARE stmt_duration;

SET @charge_mode_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_token_usage' AND column_name = 'charge_mode'
);
SET @ddl = IF(@charge_mode_exists = 0,
    'ALTER TABLE `ai_token_usage` ADD COLUMN `charge_mode` VARCHAR(32) NOT NULL DEFAULT ''PLATFORM_CHARGED'' COMMENT ''billing mode'' AFTER `duration_ms`',
    'SELECT 1');
PREPARE stmt_charge_mode FROM @ddl;
EXECUTE stmt_charge_mode;
DEALLOCATE PREPARE stmt_charge_mode;

-- Historical rows did not store prices. Split their existing total cost by the
-- default 2/8 CNY-per-million-token ratio; cost_micros remains authoritative.
UPDATE `ai_token_usage`
SET `charge_mode` = CASE
    WHEN `platform_token` = 0 THEN 'SELF_KEY'
    WHEN `cost_micros` = 0 THEN 'ADMIN_EXEMPT'
    ELSE 'PLATFORM_CHARGED'
END
WHERE `charge_mode` = 'PLATFORM_CHARGED' AND (`cost_micros` = 0 OR `platform_token` = 0);

UPDATE `ai_token_usage`
SET `input_cost_micros` = CASE
    WHEN `cost_micros` <= 0 THEN 0
    WHEN `prompt_tokens` <= 0 THEN 0
    WHEN `completion_tokens` <= 0 THEN `cost_micros`
    ELSE FLOOR(`cost_micros` * (`prompt_tokens` * 2) / (`prompt_tokens` * 2 + `completion_tokens` * 8))
END
WHERE `cost_micros` > 0
  AND `input_cost_micros` = 0
  AND `output_cost_micros` = 0;

UPDATE `ai_token_usage`
SET `output_cost_micros` = `cost_micros` - `input_cost_micros`
WHERE `cost_micros` > `input_cost_micros`
  AND `output_cost_micros` = 0;

-- Admin and self-key rows were previously stored with zero cost split. Fill
-- their model usage cost from actual token counts using the default prices.
UPDATE `ai_token_usage`
SET `input_cost_micros` = ROUND(`prompt_tokens` * 2, 0),
    `output_cost_micros` = ROUND(`completion_tokens` * 8, 0)
WHERE `input_cost_micros` = 0
  AND `output_cost_micros` = 0
  AND (`prompt_tokens` > 0 OR `completion_tokens` > 0);
