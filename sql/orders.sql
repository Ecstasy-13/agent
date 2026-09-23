CREATE TABLE IF NOT EXISTS orders (
                                      id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                                      order_no      VARCHAR(32)  NOT NULL                COMMENT '订单编号',
    user_id       VARCHAR(64)  NOT NULL                COMMENT '下单用户ID',
    product_name  VARCHAR(128) NOT NULL                COMMENT '商品名称',
    amount        DECIMAL(10,2) NOT NULL               COMMENT '订单金额',
    status        VARCHAR(16)  NOT NULL                COMMENT '订单状态：CREATED/PAID/SHIPPED/COMPLETED/CANCELLED',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表（演示数据）';

INSERT INTO orders (order_no, user_id, product_name, amount, status) VALUES
                                                                         ('ORD20240001', 'user-001', '机械键盘', 399.00, 'SHIPPED'),
                                                                         ('ORD20240002', 'user-001', '显示器',   1299.00, 'PAID'),
                                                                         ('ORD20240003', 'user-002', '笔记本电脑', 6999.00, 'COMPLETED');