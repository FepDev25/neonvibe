package com.neonvibe.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Applies a deterministic default sort to a pageable coming from a controller,
 * but only when the client did not explicitly request one. Without a stable
 * order, offset pagination over PostgreSQL returns an arbitrary (and possibly
 * inconsistent between pages) result set.
 */
final class PageableSorts {

    private PageableSorts() {
    }

    static Pageable withDefault(Pageable pageable, Sort defaultSort) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }
}
