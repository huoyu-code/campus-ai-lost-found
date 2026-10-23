package com.campus.lostfound.post.dto;

import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PostQuery extends PageQuery {

    private PostType postType;

    private String category;

    private PostStatus status;

    private String location;

    private LocalDateTime occurrenceFrom;

    private LocalDateTime occurrenceTo;
}

