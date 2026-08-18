package com.example.aistore.repository;

import com.example.aistore.entity.CustomerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {

    long countBySentiment(String sentiment);

    @Query("select cf.sentiment, count(cf) from CustomerFeedback cf group by cf.sentiment")
    List<Object[]> countBySentimentGroup();

    @Query("select cf.emotion, count(cf) from CustomerFeedback cf group by cf.emotion")
    List<Object[]> countByEmotionGroup();

    @Query("select cf.primaryTopic, count(cf) from CustomerFeedback cf group by cf.primaryTopic")
    List<Object[]> countByTopicGroup();

    @Query("select cf.primaryTopic, count(cf) from CustomerFeedback cf where cf.sentiment = 'Negative' group by cf.primaryTopic order by count(cf) desc")
    List<Object[]> countNegativeIssuesByTopic();

    @Query("select p.id, p.name, count(cf) as cnt from CustomerFeedback cf join cf.product p " +
            "where cf.sentiment = 'Negative' group by p.id, p.name order by cnt desc")
    List<Object[]> findProductsWithMostNegativeFeedback();

    @Query("select cf from CustomerFeedback cf where cf.product.id = :productId order by cf.createdAt desc")
    List<CustomerFeedback> findByProductId(@Param("productId") Long productId);

    @Query("select cf from CustomerFeedback cf where cf.product.id = :productId and cf.sentiment = 'Negative'")
    List<CustomerFeedback> findNegativeByProductId(@Param("productId") Long productId);

    List<CustomerFeedback> findByReviewId(Long reviewId);
}