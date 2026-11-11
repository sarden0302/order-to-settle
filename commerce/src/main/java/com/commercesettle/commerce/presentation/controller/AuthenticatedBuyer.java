package com.commercesettle.commerce.presentation.controller;

import java.util.UUID;

/** Gateway가 검증해 전달한 구매자 신원. 컨트롤러 인자로 받으면 구매자 인증이 필요한 작업이 된다. */
public record AuthenticatedBuyer(UUID id) {}
