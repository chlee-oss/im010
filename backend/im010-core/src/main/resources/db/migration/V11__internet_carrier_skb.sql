-- 인터넷 통신사에 SKB(알뜰, SK브로드밴드 Btv cable/POP) 추가. 휴대폰 통신망(plan_version.network)은 그대로 SKT · KT · LGU.
ALTER TABLE internet_partner DROP CONSTRAINT internet_partner_carrier_check;
ALTER TABLE internet_partner ADD CONSTRAINT internet_partner_carrier_check CHECK (carrier IN ('SKT', 'KT', 'LGU', 'SKB'));

ALTER TABLE internet_product DROP CONSTRAINT internet_product_carrier_check;
ALTER TABLE internet_product ADD CONSTRAINT internet_product_carrier_check CHECK (carrier IN ('SKT', 'KT', 'LGU', 'SKB'));
