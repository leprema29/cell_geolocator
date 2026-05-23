package cm.cirt.bts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class PriorityService {

    @Value("${providers.priority:LOCAL_DB,OpenCellID,UnwiredLabs,Combain}")
    private String prioritiesCsv;

    public List<String> getProviderPriorities() {
        return Arrays.stream(prioritiesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
