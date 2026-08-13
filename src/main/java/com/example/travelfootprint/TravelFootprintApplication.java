package com.example.travelfootprint;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.Notification;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.PostLike;
import com.example.travelfootprint.model.PostRating;
import com.example.travelfootprint.model.PrivateMessage;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.UserFollow;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.NotificationRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.PostRatingRepository;
import com.example.travelfootprint.repository.PrivateMessageRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.repository.UserFollowRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.LocationNormalizationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TravelFootprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelFootprintApplication.class, args);
    }

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            TravelPostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            PostRatingRepository ratingRepository,
            UserFollowRepository followRepository,
            PrivateMessageRepository messageRepository,
            NotificationRepository notificationRepository,
            TripPlanRepository tripPlanRepository,
            PasswordEncoder passwordEncoder,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService,
            @Value("${app.demo.seed-enabled:true}") boolean demoSeedEnabled,
            @Value("${app.admin.bootstrap-password:}") String bootstrapAdminPassword) {
        return args -> {
            String adminPassword = demoSeedEnabled ? "123456" : bootstrapAdminPassword;
            ensureAdminUser(userRepository, passwordEncoder, adminPassword);
            if (demoSeedEnabled && userRepository.count() == 1) {
                User admin = userRepository.findByUsername("admin").orElseThrow();
                userRepository.save(admin);

                User lin = new User();
                lin.setUsername("lin");
                lin.setNickname("林海拾光");
                lin.setPasswordHash(passwordEncoder.encode("123456"));
                lin.setBio("喜欢把山海和城市夜景都装进相机里。");
                lin.setEnabled(true);
                lin.setAdmin(false);

                User yue = new User();
                yue.setUsername("yue");
                yue.setNickname("远方阿越");
                yue.setPasswordHash(passwordEncoder.encode("123456"));
                yue.setBio("旅行不只打卡，更想记录每段路上的心情。");
                yue.setEnabled(true);
                yue.setAdmin(false);

                User qing = new User();
                qing.setUsername("qing");
                qing.setNickname("青川慢旅人");
                qing.setPasswordHash(passwordEncoder.encode("123456"));
                qing.setBio("偏爱古镇和徒步路线，也在整理自己的未来旅行计划。");
                qing.setEnabled(true);
                qing.setAdmin(false);

                userRepository.save(lin);
                userRepository.save(yue);
                userRepository.save(qing);

                TravelPost westLake = new TravelPost();
                westLake.setAuthor(lin);
                westLake.setTitle("杭州西湖的晚风");
                westLake.setLocation("浙江 杭州 西湖");
                westLake.setCategory("自然风光");
                westLake.setTravelDate(LocalDate.now().minusDays(9));
                westLake.setTags("湖景,日落,慢旅行");
                westLake.setLatitude(30.2431);
                westLake.setLongitude(120.1500);
                westLake.setContent("沿着苏堤散步的时候，夕阳把湖面照得像一张金色明信片。相比赶行程，我更喜欢在这样的傍晚慢慢走。");
                westLake.setReviewStatus(contentVisibilityService.defaultPostStatus(lin, true));
                postRepository.save(westLake);

                TravelPost xiamen = new TravelPost();
                xiamen.setAuthor(yue);
                xiamen.setTitle("鼓浪屿的蓝色午后");
                xiamen.setLocation("福建 厦门 鼓浪屿");
                xiamen.setCategory("海岛滨海");
                xiamen.setTravelDate(LocalDate.now().minusDays(3));
                xiamen.setTags("海岛,建筑,拍照");
                xiamen.setLatitude(24.4486);
                xiamen.setLongitude(118.0667);
                xiamen.setContent("岛上有很多适合发呆的角落，老建筑和海风叠在一起，很容易让人忘掉时间。");
                xiamen.setReviewStatus(contentVisibilityService.defaultPostStatus(yue, true));
                postRepository.save(xiamen);

                TravelPost phoenix = new TravelPost();
                phoenix.setAuthor(qing);
                phoenix.setTitle("凤凰古城的晨雾");
                phoenix.setLocation("湖南 湘西 凤凰古城");
                phoenix.setCategory("古镇人文");
                phoenix.setTravelDate(LocalDate.now().minusDays(15));
                phoenix.setTags("古镇,清晨,人文");
                phoenix.setLatitude(27.9483);
                phoenix.setLongitude(109.5996);
                phoenix.setContent("清晨的江边很安静，木桥和吊脚楼像是慢慢从雾里浮出来，特别适合边走边拍。");
                phoenix.setReviewStatus(contentVisibilityService.defaultPostStatus(qing, true));
                postRepository.save(phoenix);

                Comment firstComment = new Comment();
                firstComment.setPost(xiamen);
                firstComment.setAuthor(lin);
                firstComment.setContent("这个色调一定很好拍，已经想把它加进下次旅行清单了。");
                firstComment.setReviewStatus(contentVisibilityService.defaultCommentStatus(admin));
                commentRepository.save(firstComment);

                Comment replyComment = new Comment();
                replyComment.setPost(xiamen);
                replyComment.setAuthor(yue);
                replyComment.setParentComment(firstComment);
                replyComment.setContent("真的很适合慢慢逛，如果你去记得留半天给海边。");
                replyComment.setReviewStatus(contentVisibilityService.defaultCommentStatus(admin));
                commentRepository.save(replyComment);

                PostLike like = new PostLike();
                like.setPost(westLake);
                like.setUser(yue);
                likeRepository.save(like);

                PostFavorite favorite = new PostFavorite();
                favorite.setPost(xiamen);
                favorite.setUser(lin);
                favoriteRepository.save(favorite);

                PostRating rating = new PostRating();
                rating.setPost(xiamen);
                rating.setUser(lin);
                rating.setScore(5);
                ratingRepository.save(rating);

                UserFollow follow = new UserFollow();
                follow.setFollower(lin);
                follow.setFollowing(yue);
                followRepository.save(follow);

                PrivateMessage message = new PrivateMessage();
                message.setSender(yue);
                message.setReceiver(lin);
                message.setContent("如果你下次来厦门，我可以把我整理的拍照路线发给你。");
                messageRepository.save(message);

                Notification notification = new Notification();
                notification.setRecipient(lin);
                notification.setActor(yue);
                notification.setType(NotificationType.MESSAGE);
                notification.setMessage("远方阿越给你发送了一条私信");
                notification.setLinkPath("/messages?userId=" + yue.getId());
                notificationRepository.save(notification);

                TripPlan plan = new TripPlan();
                plan.setOwner(qing);
                plan.setTitle("五一去青岛看海");
                plan.setDestination("山东 青岛");
                plan.setStartDate(LocalDate.now().plusDays(12));
                plan.setEndDate(LocalDate.now().plusDays(15));
                plan.setBudget(new BigDecimal("2600"));
                plan.setStatus(TripPlanStatus.PLANNED);
                plan.setNotes("想安排栈桥、八大关和小麦岛，顺便拍一组日落照片。");
                tripPlanRepository.save(plan);
            }

            userRepository.findAll().forEach(user -> {
                if ("admin".equals(user.getUsername())) {
                    user.setAdmin(true);
                }
                user.setEnabled(user.isEnabled());
                userRepository.save(user);
            });

            postRepository.findAll().forEach(post -> {
                String normalizedProvince = post.getProvince() == null
                        ? null
                        : post.getProvince().trim();
                String normalizedLocation = locationNormalizationService.normalizeDisplayLocation(
                        normalizedProvince,
                        post.getLocation());
                post.setProvince(normalizedProvince);
                post.setLocation(normalizedLocation);
                post.setReviewStatus(post.getReviewStatus());
                postRepository.save(post);
            });

            commentRepository.findAll().forEach(comment -> {
                comment.setReviewStatus(comment.getReviewStatus());
                commentRepository.save(comment);
            });
        };
    }

    private void ensureAdminUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String bootstrapPassword) {
        userRepository.findByUsername("admin").ifPresentOrElse(existing -> {
            existing.setAdmin(true);
            if (existing.getBio() == null || existing.getBio().isBlank()) {
                existing.setBio("负责站点审核、内容管理和用户维护。");
            }
            userRepository.save(existing);
        }, () -> {
            if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
                return;
            }
            User admin = new User();
            admin.setUsername("admin");
            admin.setNickname("系统管理员");
            admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
            admin.setBio("负责站点审核、内容管理和用户维护。");
            admin.setEnabled(true);
            admin.setAdmin(true);
            userRepository.save(admin);
        });
    }
}
