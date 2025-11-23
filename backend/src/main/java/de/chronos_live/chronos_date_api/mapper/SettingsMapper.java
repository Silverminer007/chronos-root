package de.chronos_live.chronos_date_api.mapper;
import de.chronos_live.chronos_date_api.domain.Settings;
import de.chronos_live.chronos_date_api.presentation.SettingsDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "cdi",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SettingsMapper {

    SettingsDto toDTO(Settings settings);

    Settings toEntity(SettingsDto dto);

    // Aktualisiert existierende Entity
    void updateEntityFromDTO(SettingsDto dto, @MappingTarget Settings settings);
}