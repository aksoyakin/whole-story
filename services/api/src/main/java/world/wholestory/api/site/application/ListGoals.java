package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.site.domain.Site;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListGoals {

    private final GoalRepository goals;
    private final ReadableSites readableSites;

    @Transactional(readOnly = true)
    public List<GoalSummary> of(UUID siteId, UUID actingUserId) {
        Site site = readableSites.require(siteId, actingUserId);
        return goals.findBySite(site.getId()).stream().map(GoalMapper::toSummary).toList();
    }
}
