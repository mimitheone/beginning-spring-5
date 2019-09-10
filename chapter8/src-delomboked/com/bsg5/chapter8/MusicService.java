package com.bsg5.chapter8;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Component
public class MusicService {
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    SongRowMapper songRowMapper;

    @Transactional
    Artist findArtistById(Integer id) {
        return jdbcTemplate.query(
                "SELECT id, name FROM artists WHERE id=?", new Object[]{id},
                (rs, rowNum) -> new Artist(rs.getInt("id"), rs.getString("name"))
        ).stream().findFirst().orElse(null);
    }

    @Transactional
    Artist findArtistByName(String name) {
        return findArtistByName(name, true);
    }

    @Transactional
    Artist findArtistByName(String name, boolean update) {
        String insertSQL = "INSERT into artists (name) values (?)";
        String selectSQL = "SELECT id, name FROM artists WHERE lower(name)=lower(?)";

        return jdbcTemplate.query(
                selectSQL,
                new Object[]{name},
                (rs, rowNum) -> new Artist(
                        rs.getInt("id"),
                        rs.getString("name")
                )
        )
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    if (update) {
                        KeyHolder keyHolder = new GeneratedKeyHolder();
                        jdbcTemplate.update(conn -> {
                            PreparedStatement ps = conn.prepareStatement(insertSQL,
                                    Statement.RETURN_GENERATED_KEYS);
                            ps.setString(1, name);
                            return ps;
                        }, keyHolder);
                        return new Artist(keyHolder.getKey().intValue(), name);
                    } else {
                        return null;
                    }
                });
    }

    @Transactional
    List<Song> getSongsForArtist(String artistName) {
        String selectSQL = "SELECT id, artist_id, name, votes " +
                "FROM songs WHERE artist_id=? " +
                "order by votes desc, name asc";
        Artist artist = findArtistByName(artistName);
        return jdbcTemplate.query(
                selectSQL, new Object[]{artist.getId()},
                songRowMapper);
    }

    @Transactional
    List<String> getMatchingSongNamesForArtist(String artistName, String prefix) {
        String selectSQL = "SELECT name FROM songs WHERE artist_id=? " +
                "and lower(name) like lower(?) " +
                "order by name asc";
        Artist artist = findArtistByName(artistName);
        return jdbcTemplate.query(
                selectSQL, new Object[]{artist.getId(), prefix + "%"},
                (rs, rowNum) -> rs.getString("name"));
    }

    @Transactional
    List<String> getMatchingArtistNames(String prefix) {
        String selectSQL = "SELECT name FROM artists WHERE " +
                "lower(name) like lower(?) " +
                "order by name asc";
        return jdbcTemplate.query(
                selectSQL,
                new Object[]{prefix + "%"},
                (rs, rowNum) -> rs.getString("name"));
    }

    @Transactional
    Song getSong(String artistName, String name) {
        String selectSQL = "SELECT id, artist_id, name, votes FROM songs " +
                "WHERE artist_id=? " +
                "and lower(name) = lower(?)";
        String insertSQL = "INSERT INTO SONGS (artist_id, name, votes) " +
                "values(?,?,?)";
        Artist artist = findArtistByName(artistName);
        Song song = jdbcTemplate.query(
                selectSQL,
                new Object[]{artist.getId(), name},
                songRowMapper)
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    jdbcTemplate.update(conn -> {
                        PreparedStatement ps = conn.prepareStatement(insertSQL,
                                Statement.RETURN_GENERATED_KEYS);
                        ps.setInt(1, artist.getId());
                        ps.setString(2, name);
                        ps.setInt(3, 0);
                        return ps;
                    }, keyHolder);
                    return new Song(keyHolder.getKey().intValue(),
                            artist.getId(),
                            name,
                            0);
                });

        return song;
    }

    @Transactional
    Song voteForSong(String artistName, String name) {
        String updateSQL = "UPDATE songs SET votes=? WHERE id=?";
        Song song = getSong(artistName, name);
        song.setVotes(song.getVotes() + 1);
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(updateSQL);
            ps.setInt(1, song.getVotes());
            ps.setInt(2, song.getId());
            return ps;
        });
        return song;
    }
}
